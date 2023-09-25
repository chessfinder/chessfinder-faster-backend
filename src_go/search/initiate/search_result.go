package main

import (
	"strconv"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
)

type SearchStatus string

const (
	InProgress        SearchStatus = "IN_PROGRESS"
	SearchedAll       SearchStatus = "SEARCHED_ALL"
	SearchedPartially SearchStatus = "SEARCHED_PARTIALLY"
)

type SearchResultRecord struct {
	SearchRequestId string       `dynamodbav:"search_request_id"`
	StartSearchAt   time.Time    `dynamodbav:"start_search_at"`
	LastExaminedAt  time.Time    `dynamodbav:"last_examined_at"`
	Examined        int          `dynamodbav:"examined"`
	Total           int          `dynamodbav:"total"`
	Matched         []string     `dynamodbav:"matched"`
	Status          SearchStatus `dynamodbav:"status"`
}

func NewSearchResultRecord(searchRequestId string, StartSearchAt time.Time, total int) SearchResultRecord {
	return SearchResultRecord{
		SearchRequestId: searchRequestId,
		StartSearchAt:   StartSearchAt,
		LastExaminedAt:  StartSearchAt,
		Examined:        0,
		Total:           total,
		Matched:         []string{},
		Status:          InProgress,
	}
}

func (record SearchResultRecord) ToDynamoDbAttributes(usersTableName string) map[string]*dynamodb.AttributeValue {
	matchedAttributes := []*dynamodb.AttributeValue{}
	for _, match := range record.Matched {
		matchedAttributes = append(matchedAttributes, &dynamodb.AttributeValue{
			S: &match,
		})
	}

	attributes := map[string]*dynamodb.AttributeValue{
		"search_request_id": {
			S: &record.SearchRequestId,
		},
		"start_search_at": {
			S: aws.String(record.StartSearchAt.Format("2006-01-02T15:04:05.000Z")),
		},
		"last_examined_at": {
			S: aws.String(record.LastExaminedAt.Format("2006-01-02T15:04:05.000Z")),
		},
		"examined": {
			N: aws.String(strconv.Itoa(record.Examined)),
		},
		"total": {
			N: aws.String(strconv.Itoa(record.Total)),
		},
		"matched": {
			L: matchedAttributes,
		},
		"status": {
			S: aws.String(string(record.Status)),
		},
	}
	return attributes
}
