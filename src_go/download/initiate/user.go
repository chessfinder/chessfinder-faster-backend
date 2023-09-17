package main

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
)

type UserRecord struct {
	Username string `json:"user_name"`
	UserId   string `json:"user_id"`
}

func (record UserRecord) toPutItem(usersTableName string) *dynamodb.PutItemInput {
	return &dynamodb.PutItemInput{
		Item: map[string]*dynamodb.AttributeValue{
			"user_name": {
				S: &record.Username,
			},
			"user_id": {
				S: &record.UserId,
			},
			"platform": {
				S: aws.String("CHESS_DOT_COM"),
			},
		},
		TableName: aws.String(usersTableName),
	}
}

type ChessDotComProfile struct {
	UserId string `json:"@id"`
}
