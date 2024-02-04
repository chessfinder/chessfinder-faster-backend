package searches

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

func Test_SearchRecord_should_be_stored_in_correct_form(t *testing.T) {

	userId := uuid.New().String()
	downloadStartedAt := db.Zuludatetime(time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC))
	board := uuid.New().String()
	searchId := NewSearchId(userId, &downloadStartedAt, board)

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
		SearchId:       searchId,
		StartAt:        startAt,
		LastExaminedAt: lastExaminedAt,
		Examined:       examined,
		Status:         status,
		Total:          total,
		Matched:        matched,
		ExpiresAt:      dynamodbattribute.UnixTime(expiresAt),
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(search)
	assert.NoError(t, err)

	expectedMarshalledItems := map[string]*dynamodb.AttributeValue{
		"search_id": {
			S: aws.String(searchId.String()),
		},
		"start_at": {
			S: aws.String("2023-10-01T11:30:17.123Z"),
		},
		"last_examined_at": {
			S: aws.String("2023-09-05T19:45:17.321Z"),
		},
		"examined": {
			N: aws.String("456"),
		},
		"status": {
			S: aws.String(string(status)),
		},
		"total": {
			N: aws.String("789"),
		},
		"matched": {
			SS: []*string{
				aws.String(matchedGame1),
				aws.String(matchedGame2),
			},
		},
		"expires_at": {
			N: aws.String(strconv.FormatInt(expiresAt.Unix(), 10)),
		},
	}

	assert.Equal(t, expectedMarshalledItems, actualMarshalledItems)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(searchesTableName),
		Item:      actualMarshalledItems,
	})
	assert.NoError(t, err)

	getArchiveOutput, err := dynamodbClient.GetItem(
		&dynamodb.GetItemInput{
			TableName: aws.String(searchesTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"search_id": {
					S: aws.String(searchId.String()),
				},
			},
		},
	)

	assert.NoError(t, err)

	actualSearch := SearchRecord{}
	err = dynamodbattribute.UnmarshalMap(getArchiveOutput.Item, &actualSearch)
	assert.NoError(t, err)

	expectedSearch := search

	assert.Equal(t, expectedSearch.StartAt, actualSearch.StartAt)
	assert.Equal(t, expectedSearch.LastExaminedAt, actualSearch.LastExaminedAt)
	assert.Equal(t, expectedSearch.Examined, actualSearch.Examined)
	assert.Equal(t, expectedSearch.Total, actualSearch.Total)
	assert.ElementsMatch(t, expectedSearch.Matched, actualSearch.Matched)
	assert.Equal(t, expectedSearch.Status, actualSearch.Status)
	assert.Equal(t, time.Time(expectedSearch.ExpiresAt).UTC(), time.Time(actualSearch.ExpiresAt).UTC())
}

func Test_SearchRecord_should_be_stored_in_correct_form_even_if_matched_field_is_empty_slice(t *testing.T) {

	userId := uuid.New().String()
	downloadStartedAt := db.Zuludatetime(time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC))
	board := uuid.New().String()
	searchId := NewSearchId(userId, &downloadStartedAt, board)

	startAt := db.Zuludatetime(time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC))
	lastExaminedAt := db.Zuludatetime(time.Date(2023, time.September, 5, 19, 45, 17, 321000000, time.UTC))
	examined := 456
	status := SearchedPartially
	total := 789
	expiresAt := time.Date(2023, time.September, 6, 19, 45, 17, 0, time.UTC)

	search := SearchRecord{
		SearchId:       searchId,
		StartAt:        startAt,
		LastExaminedAt: lastExaminedAt,
		Examined:       examined,
		Status:         status,
		Total:          total,
		Matched:        nil,
		ExpiresAt:      dynamodbattribute.UnixTime(expiresAt),
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(search)
	assert.NoError(t, err)

	expectedMarshalledItems := map[string]*dynamodb.AttributeValue{
		"search_id": {
			S: aws.String(searchId.String()),
		},
		"start_at": {
			S: aws.String("2023-10-01T11:30:17.123Z"),
		},
		"last_examined_at": {
			S: aws.String("2023-09-05T19:45:17.321Z"),
		},
		"examined": {
			N: aws.String("456"),
		},
		"status": {
			S: aws.String(string(status)),
		},
		"total": {
			N: aws.String("789"),
		},
		"matched": {
			NULL: aws.Bool(true),
		},
		"expires_at": {
			N: aws.String(strconv.FormatInt(expiresAt.Unix(), 10)),
		},
	}

	assert.Equal(t, expectedMarshalledItems, actualMarshalledItems)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(searchesTableName),
		Item:      expectedMarshalledItems,
	})
	assert.NoError(t, err)

	getArchiveOutput, err := dynamodbClient.GetItem(
		&dynamodb.GetItemInput{
			TableName: aws.String(searchesTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"search_id": {
					S: aws.String(searchId.String()),
				},
			},
		},
	)

	assert.NoError(t, err)

	actualSearch := SearchRecord{}
	err = dynamodbattribute.UnmarshalMap(getArchiveOutput.Item, &actualSearch)
	assert.NoError(t, err)

	expectedSearch := search

	assert.Equal(t, expectedSearch.LastExaminedAt, actualSearch.LastExaminedAt)
	assert.Equal(t, expectedSearch.Examined, actualSearch.Examined)
	assert.Equal(t, expectedSearch.Total, actualSearch.Total)
	assert.Nil(t, actualSearch.Matched)
	assert.Equal(t, expectedSearch.Status, actualSearch.Status)
	assert.Equal(t, time.Time(expectedSearch.ExpiresAt).UTC(), time.Time(actualSearch.ExpiresAt).UTC())
}
