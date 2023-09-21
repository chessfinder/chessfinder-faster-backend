package main

import (
	"strconv"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
)

type DownloadStatusRecord struct {
	DownloadRequestId string `json:"task_id"`
	Failed            int    `json:"failed"`
	Succeed           int    `json:"succeed"`
	Done              int    `json:"done"`
	Pending           int    `json:"pending"`
	Total             int    `json:"total"`
}

func NewDownloadStatusRecord(DownloadRequestId string, total int) DownloadStatusRecord {
	return DownloadStatusRecord{
		DownloadRequestId: DownloadRequestId,
		Failed:            0,
		Succeed:           0,
		Done:              0,
		Pending:           total,
		Total:             total,
	}
}

func (record *DownloadStatusRecord) toPutItem(downloadStatusTableName string) *dynamodb.PutItemInput {
	return &dynamodb.PutItemInput{
		Item: map[string]*dynamodb.AttributeValue{
			"task_id": {
				S: aws.String(record.DownloadRequestId),
			},
			"failed": {
				N: aws.String(strconv.Itoa(record.Failed)),
			},
			"succeed": {
				N: aws.String(strconv.Itoa(record.Succeed)),
			},
			"done": {
				N: aws.String(strconv.Itoa(record.Done)),
			},
			"pending": {
				N: aws.String(strconv.Itoa(record.Pending)),
			},
			"total": {
				N: aws.String(strconv.Itoa(record.Total)),
			},
		},
		TableName: aws.String(downloadStatusTableName),
	}
}
