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
