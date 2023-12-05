package games

import (
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/batcher"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
)

const BatchReadAmount = 100

type GamesTable struct {
	Name           string
	DynamodbClient *dynamodb.DynamoDB
}

func (table GamesTable) PutGameRecords(gameRecords []GameRecord) (err error) {

	missingGameRecordWriteRequests := []*dynamodb.WriteRequest{}
	for _, missingGameRecord := range gameRecords {
		var missingGameRecordItems map[string]*dynamodb.AttributeValue
		missingGameRecordItems, err = dynamodbattribute.MarshalMap(missingGameRecord)
		if err != nil {
			return
		}
		writeRequest := &dynamodb.WriteRequest{
			PutRequest: &dynamodb.PutRequest{
				Item: missingGameRecordItems,
			},
		}
		missingGameRecordWriteRequests = append(missingGameRecordWriteRequests, writeRequest)
	}

	missingGameRecordWriteRequestsMatrix := batcher.Batcher(missingGameRecordWriteRequests, db.MaxBatchWriteLimit)

	for _, batch := range missingGameRecordWriteRequestsMatrix {

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

func (table GamesTable) QueryGames(
	userId string,
	lastKey map[string]*dynamodb.AttributeValue,
	limit int64,
) (
	games []GameRecord,
	nextKey map[string]*dynamodb.AttributeValue,
	err error,
) {

	var queryOutput *dynamodb.QueryOutput
	queryOutput, err = table.DynamodbClient.Query(&dynamodb.QueryInput{
		TableName:              &table.Name,
		KeyConditionExpression: aws.String("user_id = :user_id"),
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":user_id": {
				S: aws.String(userId),
			},
		},
		Limit:             aws.Int64(limit),
		ExclusiveStartKey: lastKey,
	})

	if err != nil {
		return
	}
	err = dynamodbattribute.UnmarshalListOfMaps(queryOutput.Items, &games)
	if err != nil {
		return
	}
	nextKey = queryOutput.LastEvaluatedKey
	return
}
