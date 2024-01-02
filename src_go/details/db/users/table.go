package users

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
)

type UsersTable struct {
	Name           string
	DynamodbClient *dynamodb.DynamoDB
}

func (table UsersTable) PutUserRecord(userRecord UserRecord) (err error) {
	items, err := dynamodbattribute.MarshalMap(userRecord)
	if err != nil {
		return
	}

	_, err = table.DynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(table.Name),
		Item:      items,
	})

	return
}

func (table UsersTable) GetUserRecord(username string, platform Platform) (userRecord *UserRecord, err error) {
	items, err := table.DynamodbClient.GetItem(&dynamodb.GetItemInput{
		TableName: aws.String(table.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"username": {
				S: aws.String(username),
			},
			"platform": {
				S: aws.String(string(platform)),
			},
		},
	})
	if err != nil {
		return
	}

	if len(items.Item) == 0 {
		return
	}

	userRecordCadidate := UserRecord{}
	err = dynamodbattribute.UnmarshalMap(items.Item, &userRecordCadidate)
	if err != nil {
		return
	}

	userRecord = &userRecordCadidate
	return
}

func (table UsersTable) DownloadFromScratch(username string, platform Platform) (err error) {
	// update user record
	_, err = table.DynamodbClient.UpdateItem(&dynamodb.UpdateItemInput{
		TableName: aws.String(table.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"username": {
				S: aws.String(username),
			},
			"platform": {
				S: aws.String(string(platform)),
			},
		},
		UpdateExpression: aws.String("set download_from_scratch = :download_from_scratch"),
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":download_from_scratch": {
				BOOL: aws.Bool(true),
			},
		},
	})

	return
}

func (table UsersTable) DownloadInitiated(username string, platform Platform) (err error) {
	// update user record
	_, err = table.DynamodbClient.UpdateItem(&dynamodb.UpdateItemInput{
		TableName: aws.String(table.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"username": {
				S: aws.String(username),
			},
			"platform": {
				S: aws.String(string(platform)),
			},
		},
		UpdateExpression: aws.String("set download_from_scratch = :download_from_scratch"),
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":download_from_scratch": {
				BOOL: aws.Bool(false),
			},
		},
	})

	return
}
