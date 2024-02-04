package downloads

import (
	"strconv"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

func Test_DownloadRecord_should_be_stored_in_correct_form(t *testing.T) {

	userId := uuid.New().String()
	downloadId := NewDownloadId(userId)
	startAt := time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC)
	lastDownloadedAt := time.Date(2023, time.October, 5, 19, 45, 17, 321000000, time.UTC)
	expiresAt := time.Date(2023, time.December, 6, 19, 45, 17, 0, time.UTC)
	succeed := 1
	failed := 2
	done := 3
	pending := 4
	total := 7

	download := DownloadRecord{
		DownloadId:       downloadId,
		StartAt:          db.Zuludatetime(startAt),
		LastDownloadedAt: db.Zuludatetime(lastDownloadedAt),
		Succeed:          succeed,
		Failed:           failed,
		Done:             done,
		Pending:          pending,
		Total:            total,
		ExpiresAt:        dynamodbattribute.UnixTime(expiresAt),
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(download)
	assert.NoError(t, err)

	expectedMarshalledItems := map[string]*dynamodb.AttributeValue{
		"download_id": {
			S: aws.String(downloadId.String()),
		},
		"start_at": {
			S: aws.String("2023-10-01T11:30:17.123Z"),
		},
		"last_downloaded_at": {
			S: aws.String("2023-10-05T19:45:17.321Z"),
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
		"expires_at": {
			N: aws.String(strconv.FormatInt(expiresAt.Unix(), 10)),
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
					S: aws.String(downloadId.String()),
				},
			},
		},
	)

	assert.NoError(t, err)

	actualDownload := DownloadRecord{}
	err = dynamodbattribute.UnmarshalMap(getArchiveOutput.Item, &actualDownload)
	assert.NoError(t, err)

	expectedDownload := download

	assert.Equal(t, expectedDownload.StartAt, actualDownload.StartAt)
	assert.Equal(t, expectedDownload.LastDownloadedAt, actualDownload.LastDownloadedAt)
	assert.Equal(t, time.Time(expectedDownload.ExpiresAt).UTC(), time.Time(actualDownload.ExpiresAt).UTC())
	assert.Equal(t, expectedDownload.Pending, actualDownload.Pending)
	assert.Equal(t, expectedDownload.Done, actualDownload.Done)
	assert.Equal(t, expectedDownload.Failed, actualDownload.Failed)
	assert.Equal(t, expectedDownload.Succeed, actualDownload.Succeed)
	assert.Equal(t, expectedDownload.Total, actualDownload.Total)
}
