package downloads

import (
	"testing"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

const downloadsTableName = "chessfinder_dynamodb-downloads"

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

var awsSession = session.Must(session.NewSession(&awsConfig))

var dynamodbClient = dynamodb.New(awsSession)

func Test_DownloadRecord_should_be_stored_in_correct_form(t *testing.T) {
	downloadId := uuid.New().String()
	succeed := 1
	failed := 2
	done := 3
	pending := 4
	total := 7

	download := DownloadRecord{
		DownloadId: downloadId,
		Succeed:    succeed,
		Failed:     failed,
		Done:       done,
		Pending:    pending,
		Total:      total,
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(download)
	assert.NoError(t, err)

	expectedMarshalledItems := map[string]*dynamodb.AttributeValue{
		"download_id": {
			S: aws.String(downloadId),
		},
		"succeed": {
			N: aws.String("1"),
		},
		"failed": {
			N: aws.String("2"),
		},
		"done": {
			N: aws.String("3"),
		},
		"pending": {
			N: aws.String("4"),
		},
		"total": {
			N: aws.String("7"),
		},
	}

	assert.Equal(t, expectedMarshalledItems, actualMarshalledItems)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(downloadsTableName),
		Item:      actualMarshalledItems,
	})

	assert.NoError(t, err)

	getArchiveOutput, err := dynamodbClient.GetItem(
		&dynamodb.GetItemInput{
			TableName: aws.String(downloadsTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"download_id": {
					S: aws.String(downloadId),
				},
			},
		},
	)

	assert.NoError(t, err)

	actualDownload := DownloadRecord{}
	err = dynamodbattribute.UnmarshalMap(getArchiveOutput.Item, &actualDownload)
	assert.NoError(t, err)

	expectedDownload := download

	assert.Equal(t, expectedDownload, actualDownload)
}
