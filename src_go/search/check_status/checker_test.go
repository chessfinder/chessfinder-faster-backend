package main

import (
	"fmt"
	"testing"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/searches"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

var statusChecker = SearchResultChecker{
	awsConfig:         &awsConfig,
	searchesTableName: "chessfinder_dynamodb-searches",
}

var awsSession = session.Must(session.NewSession(statusChecker.awsConfig))

var dynamodbClient = dynamodb.New(awsSession)

func Test_search_result_is_delivered_if_there_is_a_search_for_given_id(t *testing.T) {
	var err error

	startOfTest := time.Now()

	searchId := uuid.New().String()

	event := events.APIGatewayV2HTTPRequest{
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "GET",
				Path:   "/api/faster/board",
			},
		},
		QueryStringParameters: map[string]string{
			"searchId": searchId,
		},
	}

	startAt, err := db.ZuluDateTimeFromString("2021-01-01T00:00:00.000Z")
	assert.NoError(t, err)
	lastExaminedAt, err := db.ZuluDateTimeFromString("2021-02-01T00:11:24.000Z")
	assert.NoError(t, err)

	consistentSearchId := searches.NewConsistentSearchId("user1", "download1", "board1")
	searchRecord := searches.SearchRecord{
		SearchId:           searchId,
		ConsistentSearchId: searches.ConsistentSearchId(consistentSearchId),
		LastExaminedAt:     lastExaminedAt,
		StartAt:            startAt,
		Examined:           15,
		Total:              100,
		Matched:            []string{"https://www.chess.com/game/live/88704743803", "https://www.chess.com/game/live/88624306385"},
		Status:             "SEARCHED_ALL",
		ExpiresAt:          dynamodbattribute.UnixTime(startOfTest.Add(24 * time.Hour)),
	}

	err = searches.SearchesTable{
		Name:           statusChecker.searchesTableName,
		DynamodbClient: dynamodbClient,
	}.PutSearchRecord(searchRecord)
	assert.NoError(t, err)

	actualResponse, err := statusChecker.Check(&event)
	assert.NoError(t, err)

	expectedResponseBody := fmt.Sprintf(`{"searchId":"%v","startAt":"2021-01-01T00:00:00Z","lastExaminedAt":"2021-02-01T00:11:24Z","examined":15,"total":100,"matched":["https://www.chess.com/game/live/88624306385","https://www.chess.com/game/live/88704743803"],"status":"SEARCHED_ALL"}`, searchId)

	assert.JSONEq(t, expectedResponseBody, actualResponse.Body, "Expected download status is not met!")
	assert.Equal(t, 200, actualResponse.StatusCode, "Expected status code is not met!")
}

func Test_search_result_not_found_is_responded_if_there_is_no_search_for_given_id(t *testing.T) {

	searchId := uuid.New().String()
	event := events.APIGatewayV2HTTPRequest{
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "GET",
				Path:   "/api/faster/board",
			},
		},
		QueryStringParameters: map[string]string{
			"searchId": searchId,
		},
	}

	actualResponse, err := api.WithRecover(statusChecker.Check)(&event)
	assert.NoError(t, err)

	expectedResponseBody := fmt.Sprintf(`{"msg": "Search result %v not found", "code": "SEARCH_RESULT_NOT_FOUND"}`, searchId)
	assert.JSONEq(t, expectedResponseBody, actualResponse.Body, "Expected error is not met!")
	assert.Equal(t, 422, actualResponse.StatusCode, "Expected status code is not met!")
}
