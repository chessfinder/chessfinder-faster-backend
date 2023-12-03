package downloads

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
)

type DownloadsTable struct {
	Name           string
	DynamodbClient *dynamodb.DynamoDB
}

func (table DownloadsTable) PutDownloadRecord(downloadRecord DownloadRecord) (err error) {
	items, err := dynamodbattribute.MarshalMap(downloadRecord)
	if err != nil {
		return
	}

	_, err = table.DynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(table.Name),
		Item:      items,
	})

	return
}

func (table DownloadsTable) GetDownloadRecord(downloadId string) (downloadRecord *DownloadRecord, err error) {
	items, err := table.DynamodbClient.GetItem(&dynamodb.GetItemInput{
		TableName: aws.String(table.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"download_id": {
				S: aws.String(downloadId),
			},
		},
	})
	if err != nil {
		return
	}

	if len(items.Item) == 0 {
		return
	}

	downloadRecordCadidate := DownloadRecord{}
	err = dynamodbattribute.UnmarshalMap(items.Item, &downloadRecordCadidate)
	if err != nil {
		return
	}

	downloadRecord = &downloadRecordCadidate
	return
}
