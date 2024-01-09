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

func Test_DownloadTable_should_persist_download_record_into_the_table(t *testing.T) {
	var err error

	downloadId := uuid.New().String()
	constistentId := NewConsistentDownloadId("user1")
	startAt := time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC)
	lastDownloadedAt := time.Date(2023, time.October, 5, 19, 45, 17, 321000000, time.UTC)
	succeed := 1
	failed := 2
	done := 3
	pending := 4
	total := 7
	expiresAt := time.Date(2023, time.December, 6, 19, 45, 17, 0, time.UTC)

	download := DownloadRecord{
		DownloadId:           downloadId,
		ConsistentDownloadId: constistentId,
		StartAt:              db.Zuludatetime(startAt),
		LastDownloadedAt:     db.Zuludatetime(lastDownloadedAt),
		Succeed:              succeed,
		Failed:               failed,
		Done:                 done,
		Pending:              pending,
		Total:                total,
		ExpiresAt:            dynamodbattribute.UnixTime(expiresAt),
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

	assert.Equal(t, expectedDownload.ConsistentDownloadId, actualDownload.ConsistentDownloadId)
	assert.Equal(t, expectedDownload.StartAt, actualDownload.StartAt)
	assert.Equal(t, expectedDownload.LastDownloadedAt, actualDownload.LastDownloadedAt)
	assert.Equal(t, time.Time(expectedDownload.ExpiresAt).UTC(), time.Time(actualDownload.ExpiresAt).UTC())
	assert.Equal(t, expectedDownload.Pending, actualDownload.Pending)
	assert.Equal(t, expectedDownload.Done, actualDownload.Done)
	assert.Equal(t, expectedDownload.Failed, actualDownload.Failed)
	assert.Equal(t, expectedDownload.Succeed, actualDownload.Succeed)
	assert.Equal(t, expectedDownload.Total, actualDownload.Total)
}

func Test_DownloadTable_should_get_download_record_from_the_table(t *testing.T) {
	var err error

	downloadId := uuid.New().String()
	constistentId := NewConsistentDownloadId("user1")
	startAt := time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC)
	lastDownloadedAt := time.Date(2023, time.October, 5, 19, 45, 17, 321000000, time.UTC)
	succeed := 1
	failed := 2
	done := 3
	pending := 4
	total := 7
	expiresAt := time.Date(2023, time.December, 6, 19, 45, 17, 0, time.UTC)

	download := DownloadRecord{
		DownloadId:           downloadId,
		ConsistentDownloadId: constistentId,
		StartAt:              db.Zuludatetime(startAt),
		LastDownloadedAt:     db.Zuludatetime(lastDownloadedAt),
		Succeed:              succeed,
		Failed:               failed,
		Done:                 done,
		Pending:              pending,
		Total:                total,
		ExpiresAt:            dynamodbattribute.UnixTime(expiresAt),
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(download)
	assert.NoError(t, err)

	expectedMarshalledItems := map[string]*dynamodb.AttributeValue{
		"download_id": {
			S: aws.String(downloadId),
		},
		"consistent_download_id": {
			S: aws.String("dXNlcjE="),
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

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownload)

	expectedDownload := download

	assert.Equal(t, expectedDownload.ConsistentDownloadId, actualDownload.ConsistentDownloadId)
	assert.Equal(t, expectedDownload.StartAt, actualDownload.StartAt)
	assert.Equal(t, expectedDownload.LastDownloadedAt, actualDownload.LastDownloadedAt)
	assert.Equal(t, time.Time(expectedDownload.ExpiresAt).UTC(), time.Time(actualDownload.ExpiresAt).UTC())
	assert.Equal(t, expectedDownload.Pending, actualDownload.Pending)
	assert.Equal(t, expectedDownload.Done, actualDownload.Done)
	assert.Equal(t, expectedDownload.Failed, actualDownload.Failed)
	assert.Equal(t, expectedDownload.Succeed, actualDownload.Succeed)
	assert.Equal(t, expectedDownload.Total, actualDownload.Total)
}

func Test_DownloadTable_should_return_nil_if_the_download_is_not_present_in_the_table(t *testing.T) {
	var err error

	downloadId := uuid.New().String()

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.Nil(t, actualDownload)
}

func Test_DownloadTable_should_increment_success(t *testing.T) {
	var err error
	expiresIn := 24 * time.Hour

	downloadId := uuid.New().String()
	constistentId := NewConsistentDownloadId("user1")
	startAt := time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC)
	lastDownloadedAt := time.Date(2023, time.October, 5, 19, 45, 17, 321000000, time.UTC)
	succeed := 1
	failed := 2
	done := 3
	pending := 4
	total := 7
	expiresAt := time.Date(2023, time.December, 6, 19, 45, 17, 0, time.UTC)

	download := DownloadRecord{
		DownloadId:           downloadId,
		ConsistentDownloadId: constistentId,
		StartAt:              db.Zuludatetime(startAt),
		LastDownloadedAt:     db.Zuludatetime(lastDownloadedAt),
		Succeed:              succeed,
		Failed:               failed,
		Done:                 done,
		Pending:              pending,
		Total:                total,
		ExpiresAt:            dynamodbattribute.UnixTime(expiresAt),
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(download)
	assert.NoError(t, err)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(downloadsTableName),
		Item:      actualMarshalledItems,
	})
	assert.NoError(t, err)

	newLastDownloadedAt := db.Zuludatetime(time.Date(2024, time.September, 6, 19, 45, 17, 321000000, time.UTC))
	newExpiresAt := time.Date(2024, time.September, 7, 19, 45, 17, 0, time.UTC)
	newSucceed := succeed + 1
	newDone := done + 1
	newPending := pending - 1

	err = downloadsTable.IncrementSuccess(download, newLastDownloadedAt, expiresIn)
	assert.NoError(t, err)

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)

	assert.Equal(t, newLastDownloadedAt, actualDownload.LastDownloadedAt)
	assert.Equal(t, newSucceed, actualDownload.Succeed)
	assert.Equal(t, newDone, actualDownload.Done)
	assert.Equal(t, newPending, actualDownload.Pending)
	assert.Equal(t, time.Time(newExpiresAt).UTC(), time.Time(actualDownload.ExpiresAt).UTC())
}

func Test_DownloadTable_should_increment_failure(t *testing.T) {
	var err error
	expiresIn := 24 * time.Hour

	downloadId := uuid.New().String()
	constistentId := NewConsistentDownloadId("user1")
	startAt := time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC)
	lastDownloadedAt := time.Date(2023, time.October, 5, 19, 45, 17, 321000000, time.UTC)
	succeed := 1
	failed := 2
	done := 3
	pending := 4
	total := 7
	expiresAt := time.Date(2023, time.December, 6, 19, 45, 17, 0, time.UTC)

	download := DownloadRecord{
		DownloadId:           downloadId,
		ConsistentDownloadId: constistentId,
		StartAt:              db.Zuludatetime(startAt),
		LastDownloadedAt:     db.Zuludatetime(lastDownloadedAt),
		Succeed:              succeed,
		Failed:               failed,
		Done:                 done,
		Pending:              pending,
		Total:                total,
		ExpiresAt:            dynamodbattribute.UnixTime(expiresAt),
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(download)
	assert.NoError(t, err)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(downloadsTableName),
		Item:      actualMarshalledItems,
	})
	assert.NoError(t, err)

	newLastDownloadedAt := db.Zuludatetime(time.Date(2024, time.September, 6, 19, 45, 17, 321000000, time.UTC))
	newExpiresAt := time.Date(2024, time.September, 7, 19, 45, 17, 0, time.UTC)
	newFailed := failed + 1
	newDone := done + 1
	newPending := pending - 1

	err = downloadsTable.IncrementFailure(download, newLastDownloadedAt, expiresIn)
	assert.NoError(t, err)

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)

	assert.Equal(t, newLastDownloadedAt, actualDownload.LastDownloadedAt)
	assert.Equal(t, newFailed, actualDownload.Failed)
	assert.Equal(t, newDone, actualDownload.Done)
	assert.Equal(t, newPending, actualDownload.Pending)
	assert.Equal(t, time.Time(newExpiresAt).UTC(), time.Time(actualDownload.ExpiresAt).UTC())
}
