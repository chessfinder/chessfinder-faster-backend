package archives

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

func Test_ArchiveRecord_should_be_stored_in_correct_form(t *testing.T) {
	userId := uuid.New().String()
	archiveId := uuid.New().String()
	resource := uuid.New().String()
	year := 2023
	month := time.October
	downloadedAt := db.Zuludatetime(time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC))
	downloaded := 123

	archive := ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		Resource:     resource,
		Year:         year,
		Month:        int(month),
		DownloadedAt: &downloadedAt,
		Downloaded:   downloaded,
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(archive)
	assert.NoError(t, err)

	expectedMarshalledItems := map[string]*dynamodb.AttributeValue{
		"user_id": {
			S: aws.String(userId),
		},
		"archive_id": {
			S: aws.String(archiveId),
		},
		"resource": {
			S: aws.String(resource),
		},
		"year": {
			N: aws.String("2023"),
		},
		"month": {
			N: aws.String("10"),
		},
		"downloaded_at": {
			S: aws.String("2023-10-01T11:30:17.123Z"),
		},
		"downloaded": {
			N: aws.String("123"),
		},
	}

	assert.Equal(t, expectedMarshalledItems, actualMarshalledItems)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(archivesTableName),
		Item:      actualMarshalledItems,
	})
	assert.NoError(t, err)

	getArchiveOutput, err := dynamodbClient.GetItem(
		&dynamodb.GetItemInput{
			TableName: aws.String(archivesTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"user_id": {
					S: aws.String(userId),
				},
				"archive_id": {
					S: aws.String(archiveId),
				},
			},
		},
	)

	assert.NoError(t, err)

	actualArchive := ArchiveRecord{}
	err = dynamodbattribute.UnmarshalMap(getArchiveOutput.Item, &actualArchive)
	assert.NoError(t, err)

	expectedArchive := archive

	assert.Equal(t, expectedArchive, actualArchive)
}

func Test_ArchiveRecord_should_be_stored_in_correct_form_even_if_some_of_its_fields_are_nils(t *testing.T) {
	userId := uuid.New().String()
	archiveId := uuid.New().String()
	resource := uuid.New().String()
	year := 2023
	month := time.October
	downloaded := 123

	archive := ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		Resource:     resource,
		Year:         year,
		Month:        int(month),
		DownloadedAt: nil,
		Downloaded:   downloaded,
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(archive)
	assert.NoError(t, err)

	expectedMarshalledItems := map[string]*dynamodb.AttributeValue{
		"user_id": {
			S: aws.String(userId),
		},
		"archive_id": {
			S: aws.String(archiveId),
		},
		"resource": {
			S: aws.String(resource),
		},
		"year": {
			N: aws.String("2023"),
		},
		"month": {
			N: aws.String("10"),
		},
		"downloaded_at": {
			NULL: aws.Bool(true),
		},
		"downloaded": {
			N: aws.String("123"),
		},
	}

	assert.Equal(t, expectedMarshalledItems, actualMarshalledItems)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(archivesTableName),
		Item:      actualMarshalledItems,
	})
	assert.NoError(t, err)

	getArchiveOutput, err := dynamodbClient.GetItem(
		&dynamodb.GetItemInput{
			TableName: aws.String(archivesTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"user_id": {
					S: aws.String(userId),
				},
				"archive_id": {
					S: aws.String(archiveId),
				},
			},
		},
	)

	assert.NoError(t, err)

	actualArchive := ArchiveRecord{}
	err = dynamodbattribute.UnmarshalMap(getArchiveOutput.Item, &actualArchive)
	assert.NoError(t, err)

	expectedArchive := archive

	assert.Equal(t, expectedArchive, actualArchive)
}
