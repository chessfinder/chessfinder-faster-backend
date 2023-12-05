package games

import (
	"testing"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

func Test_GameRecord_should_be_stored_in_correct_form(t *testing.T) {

	userId := uuid.New().String()
	archiveId := uuid.New().String()
	gameId := uuid.New().String()
	endTimestamp := int64(1696706773)
	resource := uuid.New().String()
	pgn := uuid.New().String()

	game := GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       gameId,
		Resource:     resource,
		Pgn:          pgn,
		EndTimestamp: endTimestamp,
	}

	actualMarshalledItems, err := dynamodbattribute.MarshalMap(game)
	assert.NoError(t, err)

	expectedMarshalledItems := map[string]*dynamodb.AttributeValue{
		"user_id": {
			S: aws.String(userId),
		},
		"archive_id": {
			S: aws.String(archiveId),
		},
		"game_id": {
			S: aws.String(gameId),
		},
		"resource": {
			S: aws.String(resource),
		},
		"pgn": {
			S: aws.String(pgn),
		},
		"end_timestamp": {
			N: aws.String("1696706773"),
		},
	}

	assert.Equal(t, expectedMarshalledItems, actualMarshalledItems)

	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(gamesTableName),
		Item:      actualMarshalledItems,
	})
	assert.NoError(t, err)

	getArchiveOutput, err := dynamodbClient.GetItem(
		&dynamodb.GetItemInput{
			TableName: aws.String(gamesTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"user_id": {
					S: aws.String(userId),
				},
				"game_id": {
					S: aws.String(gameId),
				},
			},
		},
	)

	assert.NoError(t, err)

	actualGame := GameRecord{}
	err = dynamodbattribute.UnmarshalMap(getArchiveOutput.Item, &actualGame)
	assert.NoError(t, err)

	expectedGame := game

	assert.Equal(t, expectedGame, actualGame)
}
