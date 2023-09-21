package main

import (
	"fmt"
	"testing"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

var statusChecker = SearchResultChecker{
	awsConfig:             &awsConfig,
	searchResultTableName: "searches",
}

func Test_search_result_is_delivered_if_there_is_a_search_for_given_id(t *testing.T) {

	awsSession, err := session.NewSession(statusChecker.awsConfig)
	if err != nil {
		assert.FailNow(t, "failed to establish aws session!")
		return
	}

	dynamodbClient := dynamodb.New(awsSession)

	searchRequestId := uuid.New().String()

	event := events.APIGatewayV2HTTPRequest{
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "GET",
				Path:   "/api/faster/board",
			},
		},
		QueryStringParameters: map[string]string{
			"searchRequestId": searchRequestId,
		},
	}

	item := map[string]*dynamodb.AttributeValue{
		"search_request_id": {S: aws.String(searchRequestId)},
		"start_search_at":   {S: aws.String("2021-01-01T00:00:00.000Z")},
		"last_examined_at":  {S: aws.String("2021-02-01T00:11:24.000Z")},
		"examined":          {N: aws.String("15")},
		"total":             {N: aws.String("100")},
		"matched":           {SS: []*string{aws.String("https://www.chess.com/game/live/88704743803"), aws.String("https://www.chess.com/game/live/88624306385")}},
		"status":            {S: aws.String("SEARCHED_ALL")},
	}

	input := &dynamodb.PutItemInput{
		Item:      item,
		TableName: aws.String(statusChecker.searchResultTableName),
	}

	_, err = dynamodbClient.PutItem(input)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	actualResponse, err := statusChecker.Check(&event)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedResponseBody := fmt.Sprintf(`{"searchRequestId":"%v","startSearchAt":"2021-01-01T00:00:00Z","lastExaminedAt":"2021-02-01T00:11:24Z","examined":15,"total":100,"matched":["https://www.chess.com/game/live/88624306385","https://www.chess.com/game/live/88704743803"],"status":"SEARCHED_ALL"}`, searchRequestId)

	assert.JSONEq(t, expectedResponseBody, actualResponse.Body, "Expected download status is not met!")
	assert.Equal(t, 200, actualResponse.StatusCode, "Expected status code is not met!")
}

func Test_search_result_not_found_is_responded_if_there_is_no_search_for_given_id(t *testing.T) {

	searchRequestId := uuid.New().String()
	event := events.APIGatewayV2HTTPRequest{
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "GET",
				Path:   "/api/faster/board",
			},
		},
		QueryStringParameters: map[string]string{
			"searchRequestId": searchRequestId,
		},
	}

	actualResponse, err := statusChecker.Check(&event)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedResponseBody := fmt.Sprintf(`{"msg": "Search result %v not found", "code": "SEARCH_RESULT_NOT_FOUND"}`, searchRequestId)
	assert.JSONEq(t, expectedResponseBody, actualResponse.Body, "Expected error is not met!")
	assert.Equal(t, 422, actualResponse.StatusCode, "Expected status code is not met!")
}
