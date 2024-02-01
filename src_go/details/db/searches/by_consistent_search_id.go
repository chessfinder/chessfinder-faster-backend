package searches

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
)

type SearchesByConsistentSearchIdIndex struct {
	Name           string
	TableName      string
	DynamodbClient *dynamodb.DynamoDB
}

func (index *SearchesByConsistentSearchIdIndex) LatestSearch(consistentSearchId ConsistentSearchId) (search *SearchRecord, err error) {

	getItemOutput, err := index.DynamodbClient.Query(&dynamodb.QueryInput{
		TableName:              &index.TableName,
		IndexName:              &index.Name,
		KeyConditionExpression: aws.String("consistent_search_id = :consistentSearchId"),
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":consistentSearchId": {
				S: aws.String(consistentSearchId.String()),
			},
		},
		ScanIndexForward: aws.Bool(false),
		Limit:            aws.Int64(1),
	})

	if err != nil {
		return
	}

	if len(getItemOutput.Items) == 0 {
		return
	}

	searchCandidate := SearchRecord{}
	err = dynamodbattribute.UnmarshalMap(getItemOutput.Items[0], &searchCandidate)
	if err != nil {
		return
	}

	search = &searchCandidate

	return
}
