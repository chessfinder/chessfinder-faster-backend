package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"testing"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/sqs"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/archives"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/searches"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/users"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

type MockedValidator struct {
	isAlwaysValid bool
}

func (s MockedValidator) Validate(requestId string, board string, logger *zap.Logger) (isValid bool, comment *string, err error) {
	return s.isAlwaysValid, nil, nil
}

var registrar = SearchRegistrar{
	userTableName:       "chessfinder_dynamodb-users",
	archivesTableName:   "chessfinder_dynamodb-archives",
	searchesTableName:   "chessfinder_dynamodb-searches",
	searchBoardQueueUrl: "http://localhost:4566/000000000000/chessfinder_sqs-SearchBoard.fifo",
	searchInfoExpiresIn: 24 * time.Hour,
	awsConfig:           &awsConfig,
}
var awsSession = session.Must(session.NewSession(&awsConfig))
var dynamodbClient = dynamodb.New(awsSession)
var svc = sqs.New(awsSession)

var usersTable = users.UsersTable{
	Name:           registrar.userTableName,
	DynamodbClient: dynamodbClient,
}

var archivesTable = archives.ArchivesTable{
	Name:           registrar.archivesTableName,
	DynamodbClient: dynamodbClient,
}

var searchesTable = searches.SearchesTable{
	Name:           registrar.searchesTableName,
	DynamodbClient: dynamodbClient,
}

func Test_SearchRegistrar_should_emit_SearchBoardCommand_for_an_existing_user_and_there_are_cached_archives(t *testing.T) {
	var err error
	startOfTest := time.Now()

	registrar.validator = MockedValidator{isAlwaysValid: true}

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)
	user := users.UserRecord{
		UserId:   userId,
		Username: username,
		Platform: users.ChessDotCom,
	}

	err = usersTable.PutUserRecord(user)
	assert.NoError(t, err)

	archive1Resource := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/10", username)
	archive1DownloadedAt := db.Zuludatetime(time.Date(2021, 10, 17, 0, 0, 0, 0, time.UTC))
	archive1 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archive1Resource,
		Resource:     archive1Resource,
		Year:         2021,
		Month:        10,
		DownloadedAt: &archive1DownloadedAt,
		Downloaded:   17,
	}

	err = archivesTable.PutArchiveRecord(archive1)
	assert.NoError(t, err)

	archive2Resource := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/11", username)
	archive2DownloadedAt := db.Zuludatetime(time.Date(2021, 11, 23, 0, 0, 0, 0, time.UTC))
	archive2 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archive2Resource,
		Resource:     archive2Resource,
		Year:         2021,
		Month:        11,
		DownloadedAt: &archive2DownloadedAt,
		Downloaded:   23,
	}

	err = archivesTable.PutArchiveRecord(archive2)
	assert.NoError(t, err)

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"username":"%v", "platform": "CHESS_DOT_COM", "board": "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/board",
			},
		},
	}

	actualResponse, err := registrar.RegisterSearchRequest(&event)
	assert.NoError(t, err)

	startOfCheck := time.Now()

	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualSearchResultResponse := SearchResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualSearchResultResponse)
	assert.NoError(t, err)

	actualSearchRecord, err := searchesTable.GetSearchRecord(actualSearchResultResponse.SearchId)
	assert.NoError(t, err)
	assert.NotNil(t, actualSearchRecord)

	assert.Equal(t, searches.InProgress, actualSearchRecord.Status)
	assert.Equal(t, 0, actualSearchRecord.Examined)
	assert.Equal(t, 40, actualSearchRecord.Total)
	assert.Nil(t, actualSearchRecord.Matched)
	assert.True(t, startOfTest.Before(actualSearchRecord.StartAt.ToTime()))
	assert.True(t, startOfCheck.After(actualSearchRecord.StartAt.ToTime()))
	assert.True(t, startOfTest.Before(actualSearchRecord.LastExaminedAt.ToTime()))
	assert.True(t, startOfCheck.After(actualSearchRecord.LastExaminedAt.ToTime()))

	assert.True(t, startOfTest.Add(registrar.searchInfoExpiresIn-time.Second).Before(time.Time(actualSearchRecord.ExpiresAt)))
	assert.True(t, startOfCheck.Add(registrar.searchInfoExpiresIn+time.Second).After(time.Time(actualSearchRecord.ExpiresAt)))

	lastCommand, err := queue.GetLastNCommands(svc, registrar.searchBoardQueueUrl, 1)
	assert.NoError(t, err)

	actualCommand := queue.SearchBoardCommand{}
	err = json.Unmarshal([]byte(*lastCommand[0].Body), &actualCommand)
	if err != nil {
		return
	}

	expectedCommand := queue.SearchBoardCommand{
		UserId:   userId,
		SearchId: actualSearchResultResponse.SearchId,
		Board:    "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????",
	}

	assert.Equal(t, expectedCommand, actualCommand, "Commands are not equal!")

}

func Test_SearchRegistrar_not_should_emit_SearchBoardCommand_for_an_existing_user_if_there_are_no_cached_archives(t *testing.T) {
	var err error

	registrar.validator = MockedValidator{isAlwaysValid: true}

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)
	user := users.UserRecord{
		UserId:   userId,
		Username: username,
		Platform: users.ChessDotCom,
	}

	err = usersTable.PutUserRecord(user)
	assert.NoError(t, err)

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"username":"%v", "platform": "CHESS_DOT_COM", "board": "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/board",
			},
		},
	}

	actualResponse, err := api.WithRecover(registrar.RegisterSearchRequest)(&event)
	assert.NoError(t, err)

	assert.Equal(t, http.StatusUnprocessableEntity, actualResponse.StatusCode, "Response status code is not 422!")

	expectedErroneousResponse := fmt.Sprintf(
		`{"code":"%v","msg":"%v"}`,
		"NO_GAME_AVAILABLE",
		fmt.Sprintf("Profile %v does not have any information about their played games!", username),
	)
	assert.JSONEq(t, expectedErroneousResponse, actualResponse.Body, "Response body is not equal!")

	amountOfCommands, err := countCommands(svc, registrar)
	assert.NoError(t, err)

	assert.Equal(t, 0, amountOfCommands, "Amount of commands is not equal!")
}

func Test_SearchRegistrar_should_not_emit_SearchBoardCommand_for_an_invalid_searchfen(t *testing.T) {
	var err error

	registrar.validator = MockedValidator{isAlwaysValid: false}

	username := uuid.New().String()

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"username":"%v", "platform": "CHESS_DOT_COM", "board": "this is not a searchfen!"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/board",
			},
		},
	}

	actualResponse, err := api.WithRecover(registrar.RegisterSearchRequest)(&event)
	assert.NoError(t, err)

	assert.Equal(t, http.StatusUnprocessableEntity, actualResponse.StatusCode, "Response status code is not 422!")

	expectedErroneousResponse := fmt.Sprintf(
		`{"code":"%v","msg":"%v"}`,
		"INVALID_SEARCH_BOARD",
		"Invalid board!",
	)
	assert.JSONEq(t, expectedErroneousResponse, actualResponse.Body, "Response body is not equal!")

	amountOfCommands, err := countCommands(svc, registrar)
	assert.NoError(t, err)

	assert.Equal(t, 0, amountOfCommands, "Amount of commands is not equal!")
}

func Test_SearchRegistrar_should_not_emit_SearchBoardCommand_for_a_non_existing_user(t *testing.T) {
	var err error

	registrar.validator = MockedValidator{isAlwaysValid: true}

	username := uuid.New().String()

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"username":"%v", "platform": "CHESS_DOT_COM", "board": "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/board",
			},
		},
	}

	actualResponse, err := api.WithRecover(registrar.RegisterSearchRequest)(&event)
	assert.NoError(t, err)

	assert.Equal(t, http.StatusUnprocessableEntity, actualResponse.StatusCode, "Response status code is not 422!")

	expectedErroneousResponse := fmt.Sprintf(
		`{"code":"%v","msg":"%v"}`,
		"PROFILE_IS_NOT_CACHED",
		fmt.Sprintf("Profile %s from %s is not cached!", username, "CHESS_DOT_COM"),
	)
	assert.JSONEq(t, expectedErroneousResponse, actualResponse.Body, "Response body is not equal!")

	amountOfCommands, err := countCommands(svc, registrar)
	assert.NoError(t, err)

	assert.Equal(t, 0, amountOfCommands, "Amount of commands is not equal!")
}

func countCommands(svc *sqs.SQS, registrar SearchRegistrar) (count int, err error) {
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
