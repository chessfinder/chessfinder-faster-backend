package main

import (
	"encoding/json"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/sqs"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/archives"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/searches"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/users"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue"
	"github.com/google/uuid"
	"go.uber.org/zap"
)

type SearchRegistrar struct {
	userTableName       string
	archivesTableName   string
	searchesTableName   string
	searchBoardQueueUrl string
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
	userCandidate, err := users.UsersTable{
		Name:           registrar.userTableName,
		DynamodbClient: dynamodbClient,
	}.GetUserRecord(searchRequest.Username, users.Platform(searchRequest.Platform))

	if err != nil {
		logger.Error("error while getting user from db", zap.Error(err))
		return
	}

	if userCandidate == nil {
		err = ProfileIsNotCached(searchRequest.Username, searchRequest.Platform)
		logger.Info("profile is not cached")
		return
	}
	user := *userCandidate

	logger = logger.With(zap.String("userId", user.UserId))

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

	searchId := uuid.New().String()
	logger = logger.With(zap.String("searchResultId", searchId))
	now := time.Now()

	searchResult := searches.NewSearchRecord(searchId, now, downloadedGames)

	logger.Info("putting search result")
	err = searches.SearchesTable{
		Name:           registrar.searchesTableName,
		DynamodbClient: dynamodbClient,
	}.PutSearchRecord(searchResult)

	if err != nil {
		logger.Error("error while persisting search record", zap.Error(err))
		return
	}

	logger.Info("sending search board command")

	searchBoardCommand := queue.SearchBoardCommand{
		UserId:   user.UserId,
		SearchId: searchId,
		Board:    searchRequest.Board,
	}

	searchBoardCommandJson, err := json.Marshal(searchBoardCommand)
	if err != nil {
		logger.Error("error while marshalling search board command")
	}

	sendMessageInput := &sqs.SendMessageInput{
		MessageBody: aws.String(string(searchBoardCommandJson)),
		QueueUrl:    aws.String(registrar.searchBoardQueueUrl),
		//fixme this should be the boeard, but that makes the test flaky. in test we need to wait for the message to be processed and forgotten by SQS. To overcome this we should generate valid boear each time. That will break the restriction of deduplication.
		MessageDeduplicationId: aws.String(searchBoardCommand.SearchId),
		MessageGroupId:         aws.String(user.UserId),
	}

	_, err = svc.SendMessage(sendMessageInput)
	if err != nil {
		logger.Error("error while sending search board command")
	}

	logger.Info("search board command sent")

	searchResponse := SearchResponse{
		SearchId: searchId,
	}

	searchResponseJson, err := json.Marshal(searchResponse)
	if err != nil {
		logger.Error("error while marshalling search response")
	}

	responseEvent = events.APIGatewayV2HTTPResponse{
		StatusCode: 200,
		Headers: map[string]string{
			"Content-Type": "application/json",
		},
		Body: string(searchResponseJson),
	}

	return
}
