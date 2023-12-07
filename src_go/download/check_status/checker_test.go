package main

import (
	"fmt"
	"testing"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/downloads"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

var statusChecker = DownloadStatusChecker{
	awsConfig:          &awsConfig,
	downloadsTableName: "chessfinder_dynamodb-downloads",
}

var awsSession = session.Must(session.NewSession(&awsConfig))
var dynamodbClient = dynamodb.New(awsSession)
var downloadsTable = downloads.DownloadsTable{
	Name:           statusChecker.downloadsTableName,
	DynamodbClient: dynamodbClient,
}

func Test_download_task_status_is_delivered_if_there_is_a_task_for_given_id(t *testing.T) {
	var err error
	downloadId := uuid.New().String()

	event := events.APIGatewayV2HTTPRequest{
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "GET",
				Path:   "/api/faster/game",
			},
		},
		QueryStringParameters: map[string]string{
			"downloadId": downloadId,
		},
	}

	dowloadRecord := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    2,
		Failed:     5,
		Done:       7,
		Pending:    3,
		Total:      10,
	}

	err = downloadsTable.PutDownloadRecord(dowloadRecord)

	assert.NoError(t, err)

	actualResponse, err := statusChecker.Check(&event)
	assert.NoError(t, err)

	expectedResponseBody := fmt.Sprintf(`{"downloadId":"%v","failed":5,"succeed":2,"done":7,"pending":3,"total":10}`, downloadId)

	assert.JSONEq(t, expectedResponseBody, actualResponse.Body, "Expected download status is not met!")
	assert.Equal(t, 200, actualResponse.StatusCode, "Expected status code is not met!")
}

func Test_download_request_not_found_is_responded_if_there_is_no_task_for_given_id(t *testing.T) {

	downloadId := uuid.New().String()

	event := events.APIGatewayV2HTTPRequest{
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "GET",
				Path:   "/api/faster/game",
			},
		},
		QueryStringParameters: map[string]string{
			"downloadId": downloadId,
		},
	}

	actualResponse, err := api.WithRecover(statusChecker.Check)(&event)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedResponseBody := fmt.Sprintf(`{"code":"DOWNLOAD_REQUEST_NOT_FOUND","msg":"Download request %v not found"}`, downloadId)

	assert.JSONEq(t, expectedResponseBody, actualResponse.Body, "Expected error is not met!")
	assert.Equal(t, 422, actualResponse.StatusCode, "Expected status code is not met!")
}
