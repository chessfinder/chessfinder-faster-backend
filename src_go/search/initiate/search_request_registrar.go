package main

import (
	"encoding/json"
	"errors"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/aws/aws-sdk-go/service/sqs"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/api"
	"github.com/google/uuid"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/search/initiate/validation"
)

type SearchRequestRegistrar struct {
	userTableName         string
	archivesTableName     string
	searchResultTableName string
	searchBoardQueueUrl   string
	awsConfig             *aws.Config
}

func (registrar *SearchRequestRegistrar) RegisterSearchRequest(event *events.APIGatewayV2HTTPRequest) (responseEvent events.APIGatewayV2HTTPResponse, err error) {
	responseEvent, err = registrar.downloadArchiveAndDistributeDonwloadGameCommandsFastFail(event)
	if err != nil {
		switch err := err.(type) {
		case api.ApiError:
			responseEvent = err.ToResponseEvent()
			return responseEvent, nil
		default:
			panic(err)
		}
	}
	return
}

func (registrar *SearchRequestRegistrar) downloadArchiveAndDistributeDonwloadGameCommandsFastFail(event *events.APIGatewayV2HTTPRequest) (responseEvent events.APIGatewayV2HTTPResponse, err error) {
	config := zap.NewProductionConfig()
	config.OutputPaths = []string{"stdout"}
	timeEncoder := func(t time.Time, enc zapcore.PrimitiveArrayEncoder) {
		enc.AppendString(t.UTC().Format("2006-01-02T15:04:05.000Z"))
	} // Log to stdout
	config.EncoderConfig.EncodeTime = timeEncoder

	// Create the logger from the configuration
	logger, err := config.Build()
	if err != nil {
		panic(err)
	}
	logger = logger.With(zap.String("requestId", event.RequestContext.RequestID))
	defer logger.Sync()

	awsSession, err := session.NewSession(registrar.awsConfig)
	if err != nil {
		logger.Error("impossible to create an AWS session!")
		return
	}
	dynamodbClient := dynamodb.New(awsSession)
	svc := sqs.New(awsSession)

	method := event.RequestContext.HTTP.Method
	path := event.RequestContext.HTTP.Path

	if path != "/api/faster/board" || method != "POST" {
		logger.Error("search request registrar is attached to a wrong route!")
		// fixme check if error is 500. if not consider panicing
		err = errors.New("not supported")
		return
	}

	searchRequest := SearchRequest{}
	err = json.Unmarshal([]byte(event.Body), &searchRequest)
	if err != nil {
		logger.Error("error while unmarshalling search request", zap.Error(err), zap.String("body", event.Body))
		err = api.InvalidBody
	}

	logger.Info("validating board", zap.String("board", searchRequest.Board))
	if !validation.ValidateBoard(searchRequest.Board) {
		logger.Info("invalid board", zap.String("board", searchRequest.Board))
		err = InvalidSearchBoard
		return
	}

	logger.Info("fetching user from db", zap.String("user", searchRequest.User))
	user, err := registrar.getUserFromDb(searchRequest, logger, dynamodbClient)
	if err != nil {
		return
	}

	logger.Info("fetching archives from db", zap.String("user", user.UserId))
	archives, err := registrar.getArchivesFromDb(user, logger, dynamodbClient)
	if err != nil {
		return
	}

	downloadedGames := 0
	for _, archive := range archives {
		downloadedGames += archive.Downloaded
	}

	if downloadedGames == 0 {
		logger.Info("no game available", zap.String("user", user.UserId))
		err = NoGameAvailable(user)
		return
	}

	searchResultId := uuid.New().String()
	now := time.Now()

	searchResult := NewSearchResultRecord(searchResultId, now, downloadedGames)

	logger.Info("putting search result", zap.String("searchResultId", searchResultId), zap.String("user", user.UserId))
	err = putSearchResult(dynamodbClient, registrar, searchResult)
	if err != nil {
		logger.Error("error while putting search result",
			zap.Error(err),
			zap.String("searchResultId", searchResultId),
			zap.String("user", user.UserId),
		)
		return
	}

	searchBoardCommand := SearchBoardCommand{
		UserId:          user.UserId,
		SearchRequestId: searchResultId,
		Board:           searchRequest.Board,
	}

	searchBoardCommandJson, err := json.Marshal(searchBoardCommand)
	if err != nil {
		logger.Error("error while marshalling search board command",
			zap.Error(err),
			zap.String("searchResultId", searchResultId),
			zap.String("user", user.UserId),
		)
	}

	sendMessageInput := &sqs.SendMessageInput{
		MessageBody:            aws.String(string(searchBoardCommandJson)),
		QueueUrl:               aws.String(registrar.searchBoardQueueUrl),
		MessageDeduplicationId: aws.String(searchResultId),
		MessageGroupId:         aws.String(user.UserId),
	}

	_, err = svc.SendMessage(sendMessageInput)
	if err != nil {
		logger.Error("error while sending search board command",
			zap.Error(err),
			zap.String("searchResultId", searchResultId),
			zap.String("user", user.UserId),
		)
	}

	searchResponse := SearchResponse{
		SearchResultId: searchResultId,
	}

	searchResponseJson, err := json.Marshal(searchResponse)
	if err != nil {
		logger.Error("error while marshalling search response",
			zap.Error(err),
			zap.String("searchResultId", searchResultId),
			zap.String("user", user.UserId),
		)
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

func (registrar *SearchRequestRegistrar) getUserFromDb(
	searchRequest SearchRequest,
	logger *zap.Logger,
	dynamodbClient *dynamodb.DynamoDB,
) (user UserRecord, err error) {
	getItemInput := &dynamodb.GetItemInput{
		TableName: aws.String(registrar.userTableName),
		Key: map[string]*dynamodb.AttributeValue{
			"user_name": {
				S: aws.String(searchRequest.User),
			},
			"platform": {
				S: aws.String(string(searchRequest.Platform)),
			},
		},
	}
	getItemOutput, err := dynamodbClient.GetItem(getItemInput)
	if err != nil {
		logger.Error("error while getting user from db", zap.Error(err), zap.String("user", searchRequest.User))
		return
	}
	if getItemOutput.Item == nil {
		err = ProfileIsNotCached(searchRequest.User, searchRequest.Platform)
		logger.Info("profile is not cached", zap.String("userId", searchRequest.User))
		return
	}
	err = dynamodbattribute.UnmarshalMap(getItemOutput.Item, &user)
	if err != nil {
		return
	}
	return
}

func (registrar *SearchRequestRegistrar) getArchivesFromDb(
	user UserRecord,
	logger *zap.Logger,
	dynamodbClient *dynamodb.DynamoDB,
) (archives []ArchiveRecord, err error) {
	getItemInput := &dynamodb.QueryInput{
		TableName: aws.String(registrar.archivesTableName),
		KeyConditions: map[string]*dynamodb.Condition{
			"user_id": {
				ComparisonOperator: aws.String("EQ"),
				AttributeValueList: []*dynamodb.AttributeValue{
					{
						S: aws.String(user.UserId),
					},
				},
			},
		},
	}
	getItemOutput, err := dynamodbClient.Query(getItemInput)
	if err != nil {
		logger.Error("error while getting archives from db", zap.Error(err), zap.String("user", user.UserId))
		return
	}
	if getItemOutput.Items == nil {
		logger.Info("no archives found for user", zap.String("user", user.UserId))
		return
	}
	err = dynamodbattribute.UnmarshalListOfMaps(getItemOutput.Items, &archives)
	if err != nil {
		logger.Error("error while unmarshalling archives from db", zap.Error(err), zap.String("user", user.UserId))
		return
	}
	return
}

func putSearchResult(dynamodbClient *dynamodb.DynamoDB, registrar *SearchRequestRegistrar, searchResult SearchResultRecord) (err error) {
	searchResultItem := searchResult.ToDynamoDbAttributes(registrar.searchResultTableName)

	putSearchResultRequest := &dynamodb.PutItemInput{
		TableName: aws.String(registrar.searchResultTableName),
		Item:      searchResultItem,
	}

	_, err = dynamodbClient.PutItem(putSearchResultRequest)
	if err != nil {
		return
	}

	return
}
