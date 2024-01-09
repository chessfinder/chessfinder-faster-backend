package downloads

import (
	"strconv"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
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

func (table DownloadsTable) IncrementSuccess(currentDownload DownloadRecord, now db.ZuluDateTime, expiresIn time.Duration) (err error) {
	pending := currentDownload.Pending - 1
	done := currentDownload.Done + 1
	succeed := currentDownload.Succeed + 1
	expiresAt := now.ToTime().Add(expiresIn).Unix()
	_, err = table.DynamodbClient.UpdateItem(&dynamodb.UpdateItemInput{
		TableName: aws.String(table.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"download_id": {
				S: aws.String(currentDownload.DownloadId),
			},
		},
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":pending": {
				N: aws.String(strconv.Itoa(pending)),
			},
			":done": {
				N: aws.String(strconv.Itoa(done)),
			},
			":succeed": {
				N: aws.String(strconv.Itoa(succeed)),
			},
			":lastExaminedAt": {
				S: aws.String(now.String()),
			},
			":expiresAt": {
				N: aws.String(strconv.FormatInt(expiresAt, 10)),
			},
		},
		UpdateExpression: aws.String("SET pending = :pending, done = :done, succeed = :succeed, last_downloaded_at = :lastExaminedAt, expires_at = :expiresAt"),
	})
	return
}

func (table DownloadsTable) IncrementFailure(currentDownload DownloadRecord, now db.ZuluDateTime, expiresIn time.Duration) (err error) {
	pending := currentDownload.Pending - 1
	done := currentDownload.Done + 1
	faild := currentDownload.Failed + 1
	expiresAt := now.ToTime().Add(expiresIn).Unix()
	_, err = table.DynamodbClient.UpdateItem(&dynamodb.UpdateItemInput{
		TableName: aws.String(table.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"download_id": {
				S: aws.String(currentDownload.DownloadId),
			},
		},
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":pending": {
				N: aws.String(strconv.Itoa(pending)),
			},
			":done": {
				N: aws.String(strconv.Itoa(done)),
			},
			":failed": {
				N: aws.String(strconv.Itoa(faild)),
			},
			":lastExaminedAt": {
				S: aws.String(now.String()),
			},
			":expiresAt": {
				N: aws.String(strconv.FormatInt(expiresAt, 10)),
			},
		},
		UpdateExpression: aws.String("SET pending = :pending, done = :done, failed = :failed, last_downloaded_at = :lastExaminedAt, expires_at = :expiresAt"),
	})
	return
}
