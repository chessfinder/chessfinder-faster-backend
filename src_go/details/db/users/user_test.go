package users

import (
	"testing"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/stretchr/testify/assert"
)

const usersTableName = "chessfinder_dynamodb-users"

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

var awsSession = session.Must(session.NewSession(&awsConfig))

var dynamodbClient = dynamodb.New(awsSession)

func Test_UserRecord_should_be_stored_in_correct_form(t *testing.T) {

	username := "username"
	platform := ChessDotCom
	userId := "userId"

	user := UserRecord{
		Username: username,
		Platform: platform,
		UserId:   userId,
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(user)
	assert.NoError(t, err)

	expectedMarshalledItems := map[string]*dynamodb.AttributeValue{
		"username": {
			S: aws.String(username),
		},
		"platform": {
			S: aws.String(string(platform)),
		},
		"user_id": {
			S: aws.String(userId),
		},
	}

	assert.Equal(t, expectedMarshalledItems, actualMarshalledItems)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(usersTableName),
		Item:      actualMarshalledItems,
	})
	assert.NoError(t, err)

	getArchiveOutput, err := dynamodbClient.GetItem(
		&dynamodb.GetItemInput{
			TableName: aws.String(usersTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"username": {
					S: aws.String(username),
				},
				"platform": {
					S: aws.String(string(platform)),
				},
			},
		},
	)

	assert.NoError(t, err)

	actualUser := UserRecord{}
	err = dynamodbattribute.UnmarshalMap(getArchiveOutput.Item, &actualUser)
	assert.NoError(t, err)

	expectedUser := user

	assert.Equal(t, expectedUser, actualUser)
}
