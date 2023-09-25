package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"testing"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbiface"
	"github.com/aws/aws-sdk-go/service/sqs"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

var registrar = SearchRequestRegistrar{
	userTableName:         "users",
	archivesTableName:     "archives",
	searchResultTableName: "searches",
	searchBoardQueueUrl:   "http://localhost:4566/000000000000/SearchBoard.fifo",
	awsConfig:             &awsConfig,
}
var awsSession = session.Must(session.NewSession(&awsConfig))
var dynamodbClient = dynamodb.New(awsSession)
var svc = sqs.New(awsSession)

func Test_SearchRegistrar_should_emit_SearchBoardCommand_for_an_existing_user_and_there_are_cached_archives(t *testing.T) {
	var err error
	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)
	user := UserRecord{
		UserId:   userId,
		UserName: username,
		Platform: "CHESS_DOT_COM",
	}

	err = putUser(dynamodbClient, registrar, user)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	archive1 := ArchiveRecord{
		UserId:     userId,
		ArchiveId:  fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/10", username),
		Downloaded: 17,
	}

	err = putArchive(dynamodbClient, registrar, archive1)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	archive2 := ArchiveRecord{
		UserId:     userId,
		ArchiveId:  fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/11", username),
		Downloaded: 23,
	}

	err = putArchive(dynamodbClient, registrar, archive2)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"user":"%v", "platform": "CHESS_DOT_COM", "board": "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/board",
			},
		},
	}

	actualResponse, err := registrar.RegisterSearchRequest(&event)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}
	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualSearchResultResponse := SearchResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualSearchResultResponse)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	actualSearchResult, err := getSearchResultFromDb(dynamodbClient, registrar, actualSearchResultResponse.SearchResultId)

	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	assert.Equal(t, InProgress, actualSearchResult.Status, "Search status is not equal!")
	assert.Equal(t, int(0), actualSearchResult.Examined, "Examined is not equal!")
	assert.Equal(t, int(40), actualSearchResult.Total, "Total is not equal!")
	assert.Equal(t, []string{}, actualSearchResult.Matched, "Matched is not equal!")

	actualCommand, err := getTheLastCommand(svc, registrar)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedCommand := SearchBoardCommand{
		UserId:          userId,
		SearchRequestId: actualSearchResultResponse.SearchResultId,
		Board:           "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????",
	}

	assert.Equal(t, expectedCommand, *actualCommand, "Commands are not equal!")

}

func Test_SearchRegistrar_not_should_emit_SearchBoardCommand_for_an_existing_user_if_there_are_no_cached_archives(t *testing.T) {
	var err error

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)
	user := UserRecord{
		UserId:   userId,
		UserName: username,
		Platform: "CHESS_DOT_COM",
	}

	err = putUser(dynamodbClient, registrar, user)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"user":"%v", "platform": "CHESS_DOT_COM", "board": "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/board",
			},
		},
	}

	actualResponse, err := registrar.RegisterSearchRequest(&event)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}
	assert.Equal(t, http.StatusUnprocessableEntity, actualResponse.StatusCode, "Response status code is not 422!")

	expectedErroneousResponse := fmt.Sprintf(
		`{"code":"%v","msg":"%v"}`,
		"NO_GAME_AVAILABLE",
		fmt.Sprintf("Profile %v does not have any information about their played games!", username),
	)
	assert.JSONEq(t, expectedErroneousResponse, actualResponse.Body, "Response body is not equal!")

	amountOfCommands, err := countCommands(svc, registrar)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	assert.Equal(t, 0, amountOfCommands, "Amount of commands is not equal!")
}

func Test_SearchRegistrar_should_not_emit_SearchBoardCommand_for_an_invalid_searchfen(t *testing.T) {
	var err error

	username := uuid.New().String()

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"user":"%v", "platform": "CHESS_DOT_COM", "board": "this is not a searchfen!"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/board",
			},
		},
	}

	actualResponse, err := registrar.RegisterSearchRequest(&event)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}
	assert.Equal(t, http.StatusUnprocessableEntity, actualResponse.StatusCode, "Response status code is not 422!")

	expectedErroneousResponse := fmt.Sprintf(
		`{"code":"%v","msg":"%v"}`,
		"INVALID_SEARCH_BOARD",
		"Invalid board!",
	)
	assert.JSONEq(t, expectedErroneousResponse, actualResponse.Body, "Response body is not equal!")

	amountOfCommands, err := countCommands(svc, registrar)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	assert.Equal(t, 0, amountOfCommands, "Amount of commands is not equal!")
}

func Test_SearchRegistrar_should_not_emit_SearchBoardCommand_for_a_non_existing_user(t *testing.T) {
	var err error

	username := uuid.New().String()

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"user":"%v", "platform": "CHESS_DOT_COM", "board": "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/board",
			},
		},
	}

	actualResponse, err := registrar.RegisterSearchRequest(&event)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}
	assert.Equal(t, http.StatusUnprocessableEntity, actualResponse.StatusCode, "Response status code is not 422!")

	expectedErroneousResponse := fmt.Sprintf(
		`{"code":"%v","msg":"%v"}`,
		"PROFILE_IS_NOT_CACHED",
		fmt.Sprintf("Profile %s from %s is not cached!", username, "CHESS_DOT_COM"),
	)
	assert.JSONEq(t, expectedErroneousResponse, actualResponse.Body, "Response body is not equal!")

	amountOfCommands, err := countCommands(svc, registrar)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	assert.Equal(t, 0, amountOfCommands, "Amount of commands is not equal!")
}

func putUser(dynamodbClient dynamodbiface.DynamoDBAPI, registrar SearchRequestRegistrar, user UserRecord) (err error) {

	userItem, err := dynamodbattribute.MarshalMap(user)
	if err != nil {
		return
	}

	putUserRequest := &dynamodb.PutItemInput{
		TableName: aws.String(registrar.userTableName),
		Item:      userItem,
	}

	_, err = dynamodbClient.PutItem(putUserRequest)
	if err != nil {
		return
	}

	return
}

func putArchive(dynamodbClient dynamodbiface.DynamoDBAPI, registrar SearchRequestRegistrar, archive ArchiveRecord) (err error) {

	archiveItem, err := dynamodbattribute.MarshalMap(archive)
	if err != nil {
		return
	}

	putArchiveRequest := &dynamodb.PutItemInput{
		TableName: aws.String(registrar.archivesTableName),
		Item:      archiveItem,
	}

	_, err = dynamodbClient.PutItem(putArchiveRequest)
	if err != nil {
		return
	}

	return
}

func getSearchResultFromDb(dynamodbClient dynamodbiface.DynamoDBAPI, registrar SearchRequestRegistrar, searchResultId string) (searchResult SearchResultRecord, err error) {
	getSearchResultRequest := &dynamodb.GetItemInput{
		TableName: aws.String(registrar.searchResultTableName),
		Key: map[string]*dynamodb.AttributeValue{
			"search_request_id": {
				S: aws.String(searchResultId),
			},
		},
	}

	getSearchResultResponse, err := dynamodbClient.GetItem(getSearchResultRequest)
	if err != nil {
		return
	}

	err = dynamodbattribute.UnmarshalMap(getSearchResultResponse.Item, &searchResult)
	if err != nil {
		return
	}

	return
}

func getTheLastCommand(svc *sqs.SQS, registrar SearchRequestRegistrar) (command *SearchBoardCommand, err error) {
	resp, err := svc.ReceiveMessage(&sqs.ReceiveMessageInput{
		QueueUrl:            &registrar.searchBoardQueueUrl,
		MaxNumberOfMessages: aws.Int64(10), // You can adjust this number
		VisibilityTimeout:   aws.Int64(30), // 30 seconds timeout for processing
		WaitTimeSeconds:     aws.Int64(0),  // Long polling
	})
	if err != nil {
		fmt.Printf("Failed to fetch message with error%v", err)
		return
	}
	for _, message := range resp.Messages {
		_, err = svc.DeleteMessage(&sqs.DeleteMessageInput{
			QueueUrl:      &registrar.searchBoardQueueUrl,
			ReceiptHandle: message.ReceiptHandle,
		})
		if err != nil {
			fmt.Printf("Failed to delete message with error%v", err)
			return
		}
		currentCommand := SearchBoardCommand{}
		body := message.Body
		err = json.Unmarshal([]byte(*body), &currentCommand)
		if err != nil {
			fmt.Printf("Failed to unmarshal message with error%v", err)
			return
		}

		command = &currentCommand
	}
	return
}

func countCommands(svc *sqs.SQS, registrar SearchRequestRegistrar) (count int, err error) {
	resp, err := svc.GetQueueAttributes(&sqs.GetQueueAttributesInput{
		QueueUrl: &registrar.searchBoardQueueUrl,
		AttributeNames: []*string{
			aws.String("ApproximateNumberOfMessages"),
		},
	})
	if err != nil {
		fmt.Printf("Failed to fetch message with error%v", err)
		return
	}

	count, err = strconv.Atoi(*resp.Attributes["ApproximateNumberOfMessages"])
	if err != nil {
		fmt.Printf("Failed to convert message count with error%v", err)
		return
	}

	return
}
