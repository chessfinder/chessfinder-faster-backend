package searches

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
)

type SearchesTable struct {
	Name           string
	DynamodbClient *dynamodb.DynamoDB
}

func (table SearchesTable) PutSearchRecord(searchRecord SearchRecord) (err error) {
	items, err := dynamodbattribute.MarshalMap(searchRecord)
	if err != nil {
		return
	}

	_, err = table.DynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(table.Name),
		Item:      items,
	})

	return
}

func (table SearchesTable) GetSearchRecord(searchId string) (searchRecord *SearchRecord, err error) {
	items, err := table.DynamodbClient.GetItem(&dynamodb.GetItemInput{
		TableName: aws.String(table.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"search_id": {
				S: aws.String(searchId),
			},
		},
	})
	if err != nil {
		return
	}

	if len(items.Item) == 0 {
		return
	}

	searchRecordCadidate := SearchRecord{}
	err = dynamodbattribute.UnmarshalMap(items.Item, &searchRecordCadidate)
	if err != nil {
		return
	}

	searchRecord = &searchRecordCadidate
	return
}
