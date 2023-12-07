package users

import (
	"testing"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

func Test_UserTable_should_persist_user_into_the_table(t *testing.T) {
	var err error

	username := uuid.New().String()
	platform := ChessDotCom
	userId := "userId"

	user := UserRecord{
		Username: username,
		Platform: platform,
		UserId:   userId,
	}

	err = usersTable.PutUserRecord(user)

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

func Test_UserRecord_should_get_the_user_from_the_table(t *testing.T) {

	username := uuid.New().String()
	platform := ChessDotCom
	userId := "userId"

	user := UserRecord{
		Username: username,
		Platform: platform,
		UserId:   userId,
	}

	items, err := dynamodbattribute.MarshalMap(user)
	assert.NoError(t, err)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(usersTableName),
		Item:      items,
	})

	assert.NoError(t, err)

	actualUserRecord, err := usersTable.GetUserRecord(username, platform)
	assert.NoError(t, err)
	assert.NotNil(t, actualUserRecord)

	expectedUserRecord := user

	assert.Equal(t, *actualUserRecord, expectedUserRecord)
}

func Test_UserRecord_should_return_nil_if_the_user_is_not_found_in_the_table(t *testing.T) {

	username := uuid.New().String()
	platform := ChessDotCom

	actualUserRecord, err := usersTable.GetUserRecord(username, platform)
	assert.NoError(t, err)
	assert.Nil(t, actualUserRecord)
}
