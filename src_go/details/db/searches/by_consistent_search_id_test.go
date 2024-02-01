package searches

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

func Test_SearchesByConsistentSearchIdIndex_should_return_the_latest_download_for_the_given_consistent_download_id(t *testing.T) {
	var err error
	err = deleteAllSearches()
	assert.NoError(t, err)

	now := time.Now().UTC()

	usertId := uuid.New().String()
	downloadId := uuid.New().String()
	board := uuid.New().String()
	consistentSearchId := NewConsistentSearchId(usertId, downloadId, board)

	oldestSearchStartsAt := now.Add(-time.Hour * 24 * 7)
	oldestSearchExpiresAt := now.Add(time.Minute)
	oldestSearch := SearchRecord{
		SearchId:           uuid.New().String(),
		ConsistentSearchId: consistentSearchId,
		StartAt:            db.Zuludatetime(oldestSearchStartsAt),
		LastExaminedAt:     db.Zuludatetime(oldestSearchStartsAt),
		Examined:           0,
		Total:              10,
		Matched:            []string{},
		Status:             InProgress,
		ExpiresAt:          dynamodbattribute.UnixTime(oldestSearchExpiresAt),
	}
	err = searchesTable.PutSearchRecord(oldestSearch)
	assert.NoError(t, err)

	latestSearchStartsAt := now.Add(-time.Hour * 24 * 6)
	latestSearchExpiresAt := now.Add(time.Minute * 2)
	latestSearch := SearchRecord{
		SearchId:           uuid.New().String(),
		ConsistentSearchId: consistentSearchId,
		StartAt:            db.Zuludatetime(latestSearchStartsAt),
		LastExaminedAt:     db.Zuludatetime(latestSearchStartsAt),
		Examined:           0,
		Total:              100,
		Matched:            []string{},
		Status:             InProgress,
		ExpiresAt:          dynamodbattribute.UnixTime(latestSearchExpiresAt),
	}

	err = searchesTable.PutSearchRecord(latestSearch)
	assert.NoError(t, err)

	time.Sleep(time.Second * 5)
	actualLatestSearch, err := searchesByConsistentSearchIdIndex.LatestSearch(consistentSearchId)
	assert.NoError(t, err)
	assert.NotNil(t, actualLatestSearch)

	assert.Equal(t, latestSearch.SearchId, actualLatestSearch.SearchId)
	// assert.Equal(t, time.Time(latestSearch.ExpiresAt).Unix(), time.Time(actualLatestSearch.ExpiresAt).Unix())

}

func Test_SearchesByConsistentSearchIdIndex_should_return_nil_if_there_are_not_any_searches_in_the_table(t *testing.T) {
	var err error
	err = deleteAllSearches()
	assert.NoError(t, err)

	usertId := uuid.New().String()
	downloadId := uuid.New().String()
	board := uuid.New().String()
	consistentSearchId := NewConsistentSearchId(usertId, downloadId, board)

	actualLatestSearch, err := searchesByConsistentSearchIdIndex.LatestSearch(consistentSearchId)
	assert.NoError(t, err)
	assert.Nil(t, actualLatestSearch)

}

func deleteAllSearches() (err error) {
	output, err := dynamodbClient.Scan(&dynamodb.ScanInput{
		TableName: aws.String(searchesTableName),
	})
	if err != nil {
		return
	}
	for _, item := range output.Items {
		_, err = dynamodbClient.DeleteItem(&dynamodb.DeleteItemInput{
			TableName: aws.String(searchesTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"search_id": {
					S: item["search_id"].S,
				},
			},
		})
		if err != nil {
			return
		}
	}
	return
}
