package main

import (
	"encoding/json"
	"strings"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/sqs"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/archives"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/downloads"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/searches"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/users"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue"
	"go.uber.org/zap"
)

type SearchRegistrar struct {
	usersTableName      string
	archivesTableName   string
	downloadsTableName  string
	searchesTableName   string
	searchBoardQueueUrl string
	searchInfoExpiresIn time.Duration
	awsConfig           *aws.Config
	validator           BoardValidator
}

func (registrar *SearchRegistrar) RegisterSearchRequest(event *events.APIGatewayV2HTTPRequest) (responseEvent events.APIGatewayV2HTTPResponse, err error) {
	logger := logging.MustCreateZuluTimeLogger()
	logger = logger.With(zap.String("requestId", event.RequestContext.RequestID))
	defer logger.Sync()

	awsSession, err := session.NewSession(registrar.awsConfig)
	if err != nil {
		logger.Panic("impossible to create an AWS session!")
		return
	}
	dynamodbClient := dynamodb.New(awsSession)
	svc := sqs.New(awsSession)

	method := event.RequestContext.HTTP.Method
	path := event.RequestContext.HTTP.Path

	if path != "/api/faster/board" || method != "POST" {
		logger.Panic("search request registrar is attached to a wrong route!")
		panic("not supported")
	}

	logger = logger.With(zap.String("method", method), zap.String("path", path))

	searchRequest := SearchRequest{}
	err = json.Unmarshal([]byte(event.Body), &searchRequest)
	if err != nil {
		logger.Error("error while unmarshalling search request", zap.Error(err), zap.String("body", event.Body))
		err = api.InvalidBody
	}

	searchRequest.Username = strings.ToLower(searchRequest.Username)

	logger = logger.With(zap.String("username", searchRequest.Username), zap.String("platform", searchRequest.Platform))
	logger = logger.With(zap.String("board", searchRequest.Board))

	logger.Info("validating board")
	if isValid, _, strangeError := registrar.validator.Validate(event.RequestContext.RequestID, searchRequest.Board, logger); !isValid || strangeError != nil {
		logger.Info("invalid board")
		if strangeError != nil {
			logger.Error("error while validating board", zap.Error(strangeError))
		}
		err = InvalidSearchBoard
		return
	}

	logger.Info("fetching user from db", zap.String("user", searchRequest.Username))
	user, err := users.UsersTable{
		Name:           registrar.usersTableName,
		DynamodbClient: dynamodbClient,
	}.GetUserRecord(searchRequest.Username, users.Platform(searchRequest.Platform))

	if err != nil {
		logger.Error("error while getting user from db", zap.Error(err))
		return
	}

	if user == nil {
		err = ProfileIsNotCached(searchRequest.Username, searchRequest.Platform)
		logger.Info("profile is not cached")
		return
	}

	logger = logger.With(zap.String("userId", user.UserId))

	logger.Info("fetching download record ...")

	searchesTable := searches.SearchesTable{
		Name:           registrar.searchesTableName,
		DynamodbClient: dynamodbClient,
	}

	downloadId := downloads.NewDownloadId(user.UserId)
	exisitngDownload, err := downloads.DownloadsTable{
		Name:           registrar.downloadsTableName,
		DynamodbClient: dynamodbClient,
	}.GetDownloadRecord(downloadId.String())

	if err != nil {
		logger.Error("error while getting download record", zap.Error(err))
		return
	}

	var exisitngDownloadStartedAt *db.ZuluDateTime = nil

	if exisitngDownload != nil {
		logger = logger.With(zap.String("downloadId", downloadId.String()))
		logger.Info("download record found")
		exisitngDownloadStartedAt = &exisitngDownload.StartAt
	}

	exisitngSearchId := searches.NewSearchId(user.UserId, exisitngDownloadStartedAt, searchRequest.Board)
	exisitngSearch, err := searchesTable.GetSearchRecord(exisitngSearchId.String())
	if err != nil {
		logger.Error("error while getting search record", zap.Error(err))
		return
	}

	if exisitngSearch != nil {
		logger.Info("search record found")
		responseEvent, err = searchIdToResponseEvent(exisitngSearch.SearchId.String())

		if err != nil {
			logger.Error("error while marshalling search response")
		}
		return
	}

	logger.Info("fetching archives from db")
	archives, err :=
		archives.ArchivesTable{
			Name:           registrar.archivesTableName,
			DynamodbClient: dynamodbClient,
		}.GetArchiveRecords(user.UserId)

	if err != nil {
		return
	}

	downloadedGames := 0
	for _, archive := range archives {
		downloadedGames += archive.Downloaded
	}

	if downloadedGames == 0 {
		logger.Info("no game available", zap.String("user", user.UserId))
		err = NoGameAvailable(user.Username)
		return
	}

	searchId := searches.NewSearchId(user.UserId, exisitngDownloadStartedAt, searchRequest.Board)
	logger = logger.With(zap.String("searchResultId", searchId.String()))
	now := time.Now()

	searchResult := searches.NewSearchRecord(searchId, now, downloadedGames, registrar.searchInfoExpiresIn)

	logger.Info("putting search result")
	err = searchesTable.PutSearchRecord(searchResult)

	if err != nil {
		logger.Error("error while persisting search record", zap.Error(err))
		return
	}

	logger.Info("sending search board command")

	searchBoardCommand := queue.SearchBoardCommand{
		UserId:   user.UserId,
		SearchId: searchId.String(),
		Board:    searchRequest.Board,
	}

	searchBoardCommandJson, err := json.Marshal(searchBoardCommand)
	if err != nil {
		logger.Error("error while marshalling search board command")
	}

	sendMessageInput := &sqs.SendMessageInput{
		MessageBody: aws.String(string(searchBoardCommandJson)),
		QueueUrl:    aws.String(registrar.searchBoardQueueUrl),
		//fixme this should be the boeard, but that makes the test flaky. in test we need to wait for the message to be processed and forgotten by SQS. To overcome this we should generate valid boead each time. That will break the restriction of deduplication.
		MessageDeduplicationId: aws.String(searchBoardCommand.SearchId),
		MessageGroupId:         aws.String(user.UserId),
	}

	_, err = svc.SendMessage(sendMessageInput)
	if err != nil {
		logger.Error("error while sending search board command", zap.Error(err))
	}

	logger.Info("search board command sent")

	responseEvent, err = searchIdToResponseEvent(searchId.String())

	if err != nil {
		logger.Error("error while marshalling search response")
	}

	return
}

func searchIdToResponseEvent(searchId string) (responseEvent events.APIGatewayV2HTTPResponse, err error) {
	searchResponse := SearchResponse{
		SearchId: searchId,
	}

	jsonBody, err := json.Marshal(searchResponse)
	if err != nil {
		return
	}

	responseEvent = events.APIGatewayV2HTTPResponse{
		StatusCode: 200,
		Body:       string(jsonBody),
		Headers: map[string]string{
			"Content-Type": "application/json",
		},
	}

	return
}
