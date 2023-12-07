package downloads

import (
	"testing"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

func Test_DownloadTable_should_persist_download_record_into_the_table(t *testing.T) {
	var err error

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

	err = downloadsTable.PutDownloadRecord(download)

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

func Test_DownloadTable_should_get_download_record_from_the_table(t *testing.T) {
	var err error

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

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownload)

	expectedDownload := download

	assert.Equal(t, expectedDownload, *actualDownload)
}

func Test_DownloadTable_should_return_nil_if_the_download_is_not_present_in_the_table(t *testing.T) {
	var err error

	downloadId := uuid.New().String()

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.Nil(t, actualDownload)
}
