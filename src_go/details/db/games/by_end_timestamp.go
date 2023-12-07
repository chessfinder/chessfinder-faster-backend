package games

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
)

type LatestGameIndex struct {
	Name           string
	TableName      string
	DynamodbClient *dynamodb.DynamoDB
}

func (index LatestGameIndex) QueryByEndTimestamp(archiveId string) (game *GameRecord, err error) {
	items, err := index.DynamodbClient.Query(&dynamodb.QueryInput{
		TableName:              aws.String(index.TableName),
		IndexName:              aws.String(index.Name),
		KeyConditionExpression: aws.String("archive_id = :archive_id"),
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":archive_id": {
				S: aws.String(archiveId),
			},
		},
		ScanIndexForward: aws.Bool(false),
		Limit:            aws.Int64(1),
	})

	if err != nil {
		return
	}

	if len(items.Items) == 0 {
		return
	}

	gameCandidate := GameRecord{}
	err = dynamodbattribute.UnmarshalMap(items.Items[0], &gameCandidate)
	if err != nil {
		return
	}

	game = &gameCandidate
	return
}
