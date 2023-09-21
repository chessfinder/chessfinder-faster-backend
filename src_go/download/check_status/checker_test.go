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

var statusChecker = DownloadStatusChecker{
	awsConfig:                &awsConfig,
	downloadRequestTableName: "tasks",
}

func Test_download_task_status_is_delivered_if_there_is_a_task_for_given_id(t *testing.T) {

	awsSession, err := session.NewSession(statusChecker.awsConfig)
	if err != nil {
		assert.FailNow(t, "failed to establish aws session!")
		return
	}

	dynamodbClient := dynamodb.New(awsSession)

	downloadRequestId := uuid.New().String()

	event := events.APIGatewayV2HTTPRequest{
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "GET",
				Path:   "/api/faster/game",
			},
		},
		QueryStringParameters: map[string]string{
			"downloadRequestId": downloadRequestId,
		},
	}

	item := map[string]*dynamodb.AttributeValue{
		"done":    {N: aws.String("7")},
		"task_id": {S: aws.String(downloadRequestId)},
		"failed":  {N: aws.String("5")},
		"pending": {N: aws.String("3")},
		"succeed": {N: aws.String("2")},
		"total":   {N: aws.String("10")},
	}
	input := &dynamodb.PutItemInput{
		Item:      item,
		TableName: aws.String(statusChecker.downloadRequestTableName),
	}

	_, err = dynamodbClient.PutItem(input)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	actualResponse, err := statusChecker.Check(&event)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedResponseBody := fmt.Sprintf(`{"downloadRequestId":"%v","failed":5,"succeed":2,"done":7,"pending":3,"total":10}`, downloadRequestId)

	assert.JSONEq(t, expectedResponseBody, actualResponse.Body, "Expected download status is not met!")
	assert.Equal(t, 200, actualResponse.StatusCode, "Expected status code is not met!")
}

func Test_download_request_not_found_is_responded_if_there_is_no_task_for_given_id(t *testing.T) {

	downloadRequestId := uuid.New().String()

	event := events.APIGatewayV2HTTPRequest{
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "GET",
				Path:   "/api/faster/game",
			},
		},
		QueryStringParameters: map[string]string{
			"downloadRequestId": downloadRequestId,
		},
	}

	actualResponse, err := statusChecker.Check(&event)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedResponseBody := fmt.Sprintf(`{"code":"DOWNLOAD_REQUEST_NOT_FOUND","msg":"Download request %v not found"}`, downloadRequestId)

	assert.JSONEq(t, expectedResponseBody, actualResponse.Body, "Expected error is not met!")
	assert.Equal(t, 422, actualResponse.StatusCode, "Expected status code is not met!")
}
