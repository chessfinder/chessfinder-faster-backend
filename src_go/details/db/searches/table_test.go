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

func Test_SearchTable_should_persist_search_record_into_the_table(t *testing.T) {
	var err error
	seachId := uuid.New().String()
	constistentSearchId := NewConsistentSearchId("user1", "download1", "board1")
	startAt := db.Zuludatetime(time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC))
	lastExaminedAt := db.Zuludatetime(time.Date(2023, time.September, 5, 19, 45, 17, 321000000, time.UTC))
	examined := 456
	status := SearchedPartially
	total := 789
	matchedGame1 := uuid.New().String()
	matchedGame2 := uuid.New().String()
	matched := []string{matchedGame1, matchedGame2}
	expiresAt := time.Date(2023, time.September, 6, 19, 45, 17, 0, time.UTC)
	search := SearchRecord{
		SearchId:           seachId,
		ConsistentSearchId: constistentSearchId,
		StartAt:            startAt,
		LastExaminedAt:     lastExaminedAt,
		Examined:           examined,
		Status:             status,
		Total:              total,
		Matched:            matched,
		ExpiresAt:          dynamodbattribute.UnixTime(expiresAt),
	}

	err = searchesTable.PutSearchRecord(search)
	assert.NoError(t, err)

	getArchiveOutput, err := dynamodbClient.GetItem(
		&dynamodb.GetItemInput{
			TableName: aws.String(searchesTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"search_id": {
					S: aws.String(seachId),
				},
			},
		},
	)

	assert.NoError(t, err)

	actualSearch := SearchRecord{}
	err = dynamodbattribute.UnmarshalMap(getArchiveOutput.Item, &actualSearch)
	assert.NoError(t, err)

	expectedSearch := search

	assert.Equal(t, expectedSearch.ConsistentSearchId, actualSearch.ConsistentSearchId)
	assert.Equal(t, expectedSearch.StartAt, actualSearch.StartAt)
	assert.Equal(t, expectedSearch.LastExaminedAt, actualSearch.LastExaminedAt)
	assert.Equal(t, expectedSearch.Examined, actualSearch.Examined)
	assert.Equal(t, expectedSearch.Total, actualSearch.Total)
	assert.ElementsMatch(t, expectedSearch.Matched, actualSearch.Matched)
	assert.Equal(t, expectedSearch.Status, actualSearch.Status)
	assert.Equal(t, time.Time(expectedSearch.ExpiresAt).UTC(), time.Time(actualSearch.ExpiresAt).UTC())
}

func Test_SearchTable_should_get_search_record_from_the_table(t *testing.T) {
	var err error
	seachId := uuid.New().String()
	constistentSearchId := NewConsistentSearchId("user1", "download1", "board1")
	startAt := db.Zuludatetime(time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC))
	lastExaminedAt := db.Zuludatetime(time.Date(2023, time.September, 5, 19, 45, 17, 321000000, time.UTC))
	examined := 456
	status := SearchedPartially
	total := 789
	matchedGame1 := uuid.New().String()
	matchedGame2 := uuid.New().String()
	matched := []string{matchedGame1, matchedGame2}
	expiresAt := time.Date(2023, time.September, 6, 19, 45, 17, 0, time.UTC)

	search := SearchRecord{
		SearchId:           seachId,
		ConsistentSearchId: constistentSearchId,
		StartAt:            startAt,
		LastExaminedAt:     lastExaminedAt,
		Examined:           examined,
		Status:             status,
		Total:              total,
		Matched:            matched,
		ExpiresAt:          dynamodbattribute.UnixTime(expiresAt),
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(search)
	assert.NoError(t, err)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(searchesTableName),
		Item:      actualMarshalledItems,
	})
	assert.NoError(t, err)

	actualSearch, err := searchesTable.GetSearchRecord(seachId)
	assert.NoError(t, err)
	assert.NotNil(t, actualSearch)

	expectedSearch := search

	assert.Equal(t, expectedSearch.ConsistentSearchId, actualSearch.ConsistentSearchId)
	assert.Equal(t, expectedSearch.StartAt, actualSearch.StartAt)
	assert.Equal(t, expectedSearch.LastExaminedAt, actualSearch.LastExaminedAt)
	assert.Equal(t, expectedSearch.Examined, actualSearch.Examined)
	assert.Equal(t, expectedSearch.Total, actualSearch.Total)
	assert.ElementsMatch(t, expectedSearch.Matched, actualSearch.Matched)
	assert.Equal(t, expectedSearch.Status, actualSearch.Status)
	assert.Equal(t, time.Time(expectedSearch.ExpiresAt).UTC(), time.Time(actualSearch.ExpiresAt).UTC())
}

func Test_SearchTable_should_return_nil_if_the_search_is_missing_from_the_table(t *testing.T) {
	var err error

	seachId := uuid.New().String()

	actualSearch, err := searchesTable.GetSearchRecord(seachId)
	assert.NoError(t, err)
	assert.Nil(t, actualSearch)
}

func Test_SearchTable_should_update_the_search_record_matchings_in_the_table(t *testing.T) {
	var err error
	expiresIn := 24 * time.Hour

	seachId := uuid.New().String()
	constistentSearchId := NewConsistentSearchId("user1", "download1", "board1")
	startAt := db.Zuludatetime(time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC))
	lastExaminedAt := db.Zuludatetime(time.Date(2023, time.September, 5, 19, 45, 17, 321000000, time.UTC))
	examined := 456
	status := SearchedPartially
	total := 789
	matchedGame1 := uuid.New().String()
	matchedGame2 := uuid.New().String()
	matched := []string{matchedGame1, matchedGame2}
	expiresAt := time.Date(2023, time.September, 6, 19, 45, 17, 321000000, time.UTC)
	search := SearchRecord{
		SearchId:           seachId,
		ConsistentSearchId: constistentSearchId,
		StartAt:            startAt,
		LastExaminedAt:     lastExaminedAt,
		Examined:           examined,
		Status:             status,
		Total:              total,
		Matched:            matched,
		ExpiresAt:          dynamodbattribute.UnixTime(expiresAt),
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(search)
	assert.NoError(t, err)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(searchesTableName),
		Item:      actualMarshalledItems,
	})
	assert.NoError(t, err)

	newLastExaminedAt := db.Zuludatetime(time.Date(2023, time.September, 6, 19, 45, 17, 321000000, time.UTC))
	newExamined := 789
	newMatchedGame1 := uuid.New().String()
	newMatchedGame2 := uuid.New().String()
	newMatched := []string{newMatchedGame1, newMatchedGame2}
	newExpiresAt := time.Date(2023, time.September, 7, 19, 45, 17, 0, time.UTC)

	err = searchesTable.UpdateMatchings(seachId, newExamined, newMatched, newLastExaminedAt, expiresIn)
	assert.NoError(t, err)

	actualSearch, err := searchesTable.GetSearchRecord(seachId)
	assert.NoError(t, err)

	assert.Equal(t, newExamined, actualSearch.Examined)
	assert.Equal(t, newLastExaminedAt, actualSearch.LastExaminedAt)
	assert.ElementsMatch(t, newMatched, actualSearch.Matched)
	assert.Equal(t, time.Time(newExpiresAt).UTC(), time.Time(actualSearch.ExpiresAt).UTC())

}

func Test_SearchTable_should_update_the_search_record_status_in_the_table(t *testing.T) {
	var err error

	seachId := uuid.New().String()
	consistentSearchId := NewConsistentSearchId("user1", "download1", "board1")
	startAt := db.Zuludatetime(time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC))
	lastExaminedAt := db.Zuludatetime(time.Date(2023, time.September, 5, 19, 45, 17, 321000000, time.UTC))
	examined := 456
	status := SearchedPartially
	total := 789
	matchedGame1 := uuid.New().String()
	matchedGame2 := uuid.New().String()
	matched := []string{matchedGame1, matchedGame2}
	expiresAt := time.Date(2023, time.September, 6, 19, 45, 17, 0, time.UTC)
	search := SearchRecord{
		SearchId:           seachId,
		ConsistentSearchId: consistentSearchId,
		StartAt:            startAt,
		LastExaminedAt:     lastExaminedAt,
		Examined:           examined,
		Status:             status,
		Total:              total,
		Matched:            matched,
		ExpiresAt:          dynamodbattribute.UnixTime(expiresAt),
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(search)
	assert.NoError(t, err)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(searchesTableName),
		Item:      actualMarshalledItems,
	})
	assert.NoError(t, err)

	newStatus := SearchedAll

	err = searchesTable.UpdateStatus(seachId, newStatus)
	assert.NoError(t, err)

	actualSearch, err := searchesTable.GetSearchRecord(seachId)
	assert.NoError(t, err)

	assert.Equal(t, newStatus, actualSearch.Status)
}
