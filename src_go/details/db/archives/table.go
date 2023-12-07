package archives

import (
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/batcher"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
)

type ArchivesTable struct {
	Name           string
	DynamodbClient *dynamodb.DynamoDB
}

func (table ArchivesTable) GetArchiveRecord(
	userId string,
	archiveId string,
) (archiveRecord *ArchiveRecord, err error) {
	items, err := table.DynamodbClient.GetItem(&dynamodb.GetItemInput{
		TableName: aws.String(table.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"user_id": {
				S: aws.String(userId),
			},
			"archive_id": {
				S: aws.String(archiveId),
			},
		},
	})

	if err != nil {
		return
	}

	if len(items.Item) == 0 {
		return
	}

	archiveRecordCadidate := ArchiveRecord{}
	err = dynamodbattribute.UnmarshalMap(items.Item, &archiveRecordCadidate)
	if err != nil {
		return
	}

	archiveRecord = &archiveRecordCadidate
	return
}

func (table ArchivesTable) GetArchiveRecords(
	userId string,
) (archiveRecords []ArchiveRecord, err error) {
	items, err := table.DynamodbClient.Query(&dynamodb.QueryInput{
		TableName:              aws.String(table.Name),
		KeyConditionExpression: aws.String("user_id = :user_id"),
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":user_id": {
				S: aws.String(userId),
			},
		},
	})

	if err != nil {
		return
	}

	if len(items.Items) == 0 {
		return
	}

	err = dynamodbattribute.UnmarshalListOfMaps(items.Items, &archiveRecords)
	if err != nil {
		return
	}

	return
}

func (table ArchivesTable) PutArchiveRecord(
	archiveRecord ArchiveRecord,
) (err error) {
	archiveRecordItems, err := dynamodbattribute.MarshalMap(archiveRecord)
	if err != nil {
		return
	}

	_, err = table.DynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(table.Name),
		Item:      archiveRecordItems,
	})

	if err != nil {
		return
	}

	return
}

func (table ArchivesTable) PutArchiveRecords(
	archiveRecords []ArchiveRecord,
) (err error) {
	archiveRecordWriteRequests := make([]*dynamodb.WriteRequest, len(archiveRecords))
	for i, archiveRecord := range archiveRecords {
		var archiveRecordItems map[string]*dynamodb.AttributeValue
		archiveRecordItems, err = dynamodbattribute.MarshalMap(archiveRecord)
		if err != nil {
			return err
		}

		writeRequest := dynamodb.WriteRequest{
			PutRequest: &dynamodb.PutRequest{
				Item: archiveRecordItems,
			},
		}

		archiveRecordWriteRequests[i] = &writeRequest
	}

	archiveRecordWriteRequestsMatrix := batcher.Batcher(archiveRecordWriteRequests, db.MaxBatchWriteLimit)

	for _, batch := range archiveRecordWriteRequestsMatrix {

		unprocessedWriteRequests := map[string][]*dynamodb.WriteRequest{
			table.Name: batch,
		}

		for len(unprocessedWriteRequests) > 0 {
			var writeOutput *dynamodb.BatchWriteItemOutput
			writeOutput, err = table.DynamodbClient.BatchWriteItem(&dynamodb.BatchWriteItemInput{
				RequestItems: unprocessedWriteRequests,
			})

			if err != nil {
				return
			}

			unprocessedWriteRequests = writeOutput.UnprocessedItems
			time.Sleep(time.Millisecond * db.BatchWriteRateInMillis)
		}
	}

	return
}
