package archives

import (
	"fmt"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

func Test_ArchiveTable_should_get_the_archive_record_from_the_table(t *testing.T) {
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

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(archivesTableName),
		Item:      actualMarshalledItems,
	})
	assert.NoError(t, err)

	actualArchive, err := archivesTable.GetArchiveRecord(userId, archiveId)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive)

	expectedArchive := archive

	assert.Equal(t, expectedArchive, *actualArchive)
}

func Test_ArchiveTable_should_get_all_the_archive_records_for_the_given_user(t *testing.T) {
	userId := uuid.New().String()

	archiveId1 := uuid.New().String()
	resource1 := uuid.New().String()
	year1 := 2023
	month1 := time.October
	downloadedAt1 := db.Zuludatetime(time.Date(2023, time.October, 1, 11, 30, 17, 123000000, time.UTC))
	downloaded1 := 123

	archive1 := ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId1,
		Resource:     resource1,
		Year:         year1,
		Month:        int(month1),
		DownloadedAt: &downloadedAt1,
		Downloaded:   downloaded1,
	}

	actualMarshalledItems1, err := dynamodbattribute.MarshalMap(archive1)
	assert.NoError(t, err)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(archivesTableName),
		Item:      actualMarshalledItems1,
	})
	assert.NoError(t, err)

	archiveId2 := uuid.New().String()
	resource2 := uuid.New().String()
	year2 := 2024
	month2 := time.November
	downloadedAt2 := db.Zuludatetime(time.Date(2024, time.November, 2, 12, 31, 18, 321000000, time.UTC))
	downloaded2 := 456

	archive2 := ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId2,
		Resource:     resource2,
		Year:         year2,
		Month:        int(month2),
		DownloadedAt: &downloadedAt2,
		Downloaded:   downloaded2,
	}

	actualMarshalledItems2, err := dynamodbattribute.MarshalMap(archive2)
	assert.NoError(t, err)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(archivesTableName),
		Item:      actualMarshalledItems2,
	})
	assert.NoError(t, err)

	actualArchives, err := archivesTable.GetArchiveRecords(userId)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchives)

	expectedArchives := []ArchiveRecord{archive1, archive2}

	assert.ElementsMatch(t, expectedArchives, actualArchives)
}

func Test_ArchiveTable_should_persist_an_archive_record_into_the_table(t *testing.T) {
	var err error

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)

	archiveId_2021_10 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/10", username)

	archive_2021_10 := ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_10,
		Resource:     archiveId_2021_10,
		Year:         2021,
		Month:        10,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	err = archivesTable.PutArchiveRecord(archive_2021_10)
	assert.NoError(t, err)

	archive_2021_10_items, err := archivesTable.DynamodbClient.GetItem(&dynamodb.GetItemInput{
		TableName: aws.String(archivesTable.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"user_id": {
				S: aws.String(userId),
			},
			"archive_id": {
				S: aws.String(archiveId_2021_10),
			},
		},
	})

	assert.NoError(t, err)

	actualArchive_2021_10 := ArchiveRecord{}
	err = dynamodbattribute.UnmarshalMap(archive_2021_10_items.Item, &actualArchive_2021_10)
	assert.NoError(t, err)

	expectedArchive_2021_10 := archive_2021_10

	assert.Equal(t, expectedArchive_2021_10, actualArchive_2021_10)
}

func Test_ArchiveTable_should_persist_all_the_archive_records_at_once_into_the_table(t *testing.T) {
	var err error

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)

	archiveId_2021_10 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/10", username)

	archive_2021_10 := ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_10,
		Resource:     archiveId_2021_10,
		Year:         2021,
		Month:        10,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	archiveId_2021_11 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/11", username)

	archive_2021_11 := ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_11,
		Resource:     archiveId_2021_11,
		Year:         2021,
		Month:        11,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	err = archivesTable.PutArchiveRecords([]ArchiveRecord{archive_2021_10, archive_2021_11})
	assert.NoError(t, err)

	archive_2021_10_items, err := archivesTable.DynamodbClient.GetItem(&dynamodb.GetItemInput{
		TableName: aws.String(archivesTable.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"user_id": {
				S: aws.String(userId),
			},
			"archive_id": {
				S: aws.String(archiveId_2021_10),
			},
		},
	})

	assert.NoError(t, err)

	actualArchive_2021_10 := ArchiveRecord{}
	err = dynamodbattribute.UnmarshalMap(archive_2021_10_items.Item, &actualArchive_2021_10)
	assert.NoError(t, err)

	expectedArchive_2021_10 := archive_2021_10

	assert.Equal(t, expectedArchive_2021_10, actualArchive_2021_10)

	archive_2021_11_items, err := archivesTable.DynamodbClient.GetItem(&dynamodb.GetItemInput{
		TableName: aws.String(archivesTable.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"user_id": {
				S: aws.String(userId),
			},
			"archive_id": {
				S: aws.String(archiveId_2021_11),
			},
		},
	})

	assert.NoError(t, err)

	actualArchive_2021_11 := ArchiveRecord{}

	err = dynamodbattribute.UnmarshalMap(archive_2021_11_items.Item, &actualArchive_2021_11)
	assert.NoError(t, err)

	expectedArchive_2021_11 := archive_2021_11

	assert.Equal(t, expectedArchive_2021_11, actualArchive_2021_11)
}

func Test_ArchiveTable_should_persist_a_lot_of_archive_records_into_the_table(t *testing.T) {
	var err error

	totalArchiveCount := 1000
	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)
	resource := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/10", username)
	year := 2021
	month := 10
	downloadedAt := db.Zuludatetime(time.Date(2021, time.October, 1, 11, 30, 17, 123000000, time.UTC))
	downloaded := 123

	archives := make([]ArchiveRecord, totalArchiveCount)

	for i := 0; i < totalArchiveCount; i++ {
		archiveId := uuid.New().String()
		archive := ArchiveRecord{
			UserId:       userId,
			ArchiveId:    archiveId,
			Resource:     resource,
			Year:         year,
			Month:        month,
			DownloadedAt: &downloadedAt,
			Downloaded:   downloaded,
		}
		archives[i] = archive
	}

	err = archivesTable.PutArchiveRecords(archives)
	assert.NoError(t, err)

	var lastKey map[string]*dynamodb.AttributeValue
	actualArchiveCount := 0

	for {
		batch := []ArchiveRecord{}

		archiveRecordsItems, err := dynamodbClient.Query(&dynamodb.QueryInput{
			TableName:              aws.String(archivesTable.Name),
			KeyConditionExpression: aws.String("user_id = :user_id"),
			ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
				":user_id": {
					S: aws.String(userId),
				},
			},
			Limit:             aws.Int64(100),
			ExclusiveStartKey: lastKey,
		})

		assert.NoError(t, err)

		if len(archiveRecordsItems.Items) == 0 {
			break
		}

		err = dynamodbattribute.UnmarshalListOfMaps(archiveRecordsItems.Items, &batch)
		assert.NoError(t, err)

		actualArchiveCount += len(batch)
		lastKey = archiveRecordsItems.LastEvaluatedKey
	}

	assert.Equal(t, totalArchiveCount, actualArchiveCount)
}
