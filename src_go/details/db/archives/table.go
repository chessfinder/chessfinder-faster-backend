package archives

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
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
