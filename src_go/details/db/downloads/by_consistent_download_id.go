package downloads

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
)

type DownloadsByConsistentDownloadIdIndex struct {
	Name           string
	TableName      string
	DynamodbClient *dynamodb.DynamoDB
}

func (index DownloadsByConsistentDownloadIdIndex) LatestDownload(consistentDownloadId ConsistentDownloadId) (download *DownloadRecord, err error) {

	getItemOutput, err := index.DynamodbClient.Query(&dynamodb.QueryInput{
		TableName:              &index.TableName,
		IndexName:              &index.Name,
		KeyConditionExpression: aws.String("consistent_download_id = :consistentDownloadId"),
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":consistentDownloadId": {
				S: aws.String(consistentDownloadId.String()),
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

	downloadCandidate := DownloadRecord{}
	err = dynamodbattribute.UnmarshalMap(getItemOutput.Items[0], &downloadCandidate)
	if err != nil {
		return
	}

	download = &downloadCandidate

	return
}
