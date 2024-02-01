package downloads

import (
	"testing"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

func Test_DownloadsByConsistentDownloadIdIndex_should_return_the_latest_download_for_the_given_consistent_download_id(t *testing.T) {
	var err error
	err = deleteAllDownloads()
	assert.NoError(t, err)

	now := time.Now().UTC()

	usertId := uuid.New().String()
	consistentDownloadId := NewConsistentDownloadId(usertId)

	oldestDownloadStartsAt := now.Add(-time.Hour * 24 * 7)
	oldestDownloadExpiresAt := now.Add(time.Minute)
	oldestDownload := DownloadRecord{
		DownloadId:           uuid.New().String(),
		ConsistentDownloadId: consistentDownloadId,
		StartAt:              db.Zuludatetime(oldestDownloadStartsAt),
		LastDownloadedAt:     db.Zuludatetime(oldestDownloadStartsAt),
		ExpiresAt:            dynamodbattribute.UnixTime(oldestDownloadExpiresAt),
		Succeed:              0,
		Failed:               10,
		Done:                 10,
		Pending:              0,
		Total:                10,
	}

	err = downloadsTable.PutDownloadRecord(oldestDownload)
	assert.NoError(t, err)

	latestDownloadStartsAt := now.Add(-time.Hour * 24 * 6)
	latestDownloadExpiresAt := now.Add(time.Minute * 2)
	latestDownload := DownloadRecord{
		DownloadId:           uuid.New().String(),
		ConsistentDownloadId: consistentDownloadId,
		StartAt:              db.Zuludatetime(latestDownloadStartsAt),
		LastDownloadedAt:     db.Zuludatetime(latestDownloadStartsAt),
		ExpiresAt:            dynamodbattribute.UnixTime(latestDownloadExpiresAt),
		Succeed:              100,
		Failed:               0,
		Done:                 100,
		Pending:              0,
		Total:                100,
	}

	err = downloadsTable.PutDownloadRecord(latestDownload)
	assert.NoError(t, err)

	actualLatestDownload, err := downloadsByConsistentDownloadIdIndex.LatestDownload(consistentDownloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualLatestDownload)

	assert.Equal(t, latestDownload.DownloadId, actualLatestDownload.DownloadId)

}

func Test_DownloadsByConsistentDownloadIdIndex_should_return_nil_if_there_are_not_any_downloads_in_the_table(t *testing.T) {
	var err error
	err = deleteAllDownloads()
	assert.NoError(t, err)

	usertId := uuid.New().String()
	consistentDownloadId := NewConsistentDownloadId(usertId)

	actualLatestDownload, err := downloadsByConsistentDownloadIdIndex.LatestDownload(consistentDownloadId)
	assert.NoError(t, err)
	assert.Nil(t, actualLatestDownload)

}

func deleteAllDownloads() (err error) {
	output, err := dynamodbClient.Scan(&dynamodb.ScanInput{
		TableName: aws.String(downloadsTableName),
	})
	if err != nil {
		return
	}
	for _, item := range output.Items {
		_, err = dynamodbClient.DeleteItem(&dynamodb.DeleteItemInput{
			TableName: aws.String(downloadsTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"download_id": {
					S: item["download_id"].S,
				},
			},
		})
		if err != nil {
			return
		}
	}
	return
}
