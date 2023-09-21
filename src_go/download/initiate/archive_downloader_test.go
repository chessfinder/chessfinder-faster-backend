package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"testing"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbiface"
	"github.com/aws/aws-sdk-go/service/sqs"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/wiremock/go-wiremock"
)

func Test_ArchiveDownloader_should_emit_DownloadGameCommands_for_all_missing_archives(t *testing.T) {
	awsConfig := aws.Config{
		Region:     aws.String("us-east-1"),
		Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
		DisableSSL: aws.Bool(true),
	}
	downloader := ArchiveDownloader{
		downloadGamesQueueUrl:    "http://localhost:4566/000000000000/DownloadGames.fifo",
		chessDotComUrl:           "http://0.0.0.0:18443",
		usersTableName:           "users",
		archivesTableName:        "archives",
		downloadRequestTableName: "tasks",
		awsConfig:                &awsConfig,
	}

	awsSession, err := session.NewSession(downloader.awsConfig)
	if err != nil {
		assert.FailNow(t, "failed to establish aws session!")
		return
	}

	dynamodbClient := dynamodb.New(awsSession)
	svc := sqs.New(awsSession)

	wiremockClient := wiremock.NewClient("http://0.0.0.0:18443")
	defer wiremockClient.Reset()

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)

	usersProfileReponseBody := fmt.Sprintf(
		`{
      "player_id": 191338281,
      "@id": "%v",
      "url": "https://www.chess.com/member/%v",
      "username": "%v",
      "followers": 10,
      "country": "https://api.chess.com/pub/country/AM",
      "last_online": 1678264516,
      "joined": 1658920370,
      "status": "premium",
      "is_streamer": false,
      "verified": false,
      "league": "Champion"
    }`,
		userId,
		username,
		username,
	)

	getUsersProfileStub := wiremock.Get(wiremock.URLPathEqualTo(fmt.Sprintf("/pub/player/%v", username))).
		WillReturnResponse(
			wiremock.NewResponse().
				WithBody(usersProfileReponseBody).
				WithHeader("Content-Type", "application/json").
				WithStatus(http.StatusOK),
		)
	err = wiremockClient.StubFor(getUsersProfileStub)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	archivesResponseBody := fmt.Sprintf(
		`{
			"archives": [
				"https://api.chess.com/pub/player/%v/games/2021/10",
				"https://api.chess.com/pub/player/%v/games/2021/11",
				"https://api.chess.com/pub/player/%v/games/2021/12"
			]
		}`, username, username, username,
	)

	getArchivesStub := wiremock.Get(wiremock.URLPathEqualTo(fmt.Sprintf("/pub/player/%v/games/archives", username))).
		WillReturnResponse(
			wiremock.NewResponse().
				WithBody(archivesResponseBody).
				WithHeader("Content-Type", "application/json").
				WithStatus(http.StatusOK),
		)
	err = wiremockClient.StubFor(getArchivesStub)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"user":"%v"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/game",
			},
		},
	}

	actualResponse, err := downloader.DownloadArchiveAndDistributeDonwloadGameCommands(&event)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualDownloadResponse := DownloadResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualDownloadResponse)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	downloadRequestId := actualDownloadResponse.TaskId

	downloadRequestStatus, err := getDownloadStatusFromDb(dynamodbClient, downloader, downloadRequestId)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedDownloadRequestStatus := DownloadStatusRecord{
		DownloadRequestId: downloadRequestId,
		Failed:            0,
		Succeed:           0,
		Done:              0,
		Pending:           3,
		Total:             3,
	}

	assert.Equal(t, expectedDownloadRequestStatus, downloadRequestStatus, "Download request status is not equal!")

	verifyGetUsersProfileStub, err := wiremockClient.Verify(getUsersProfileStub.Request(), 1)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}
	assert.Equal(t, true, verifyGetUsersProfileStub, fmt.Sprintf("Stub of getting user profile %v was not called!", username))

	verifyGetArchivesStub, err := wiremockClient.Verify(getArchivesStub.Request(), 1)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	assert.Equal(t, true, verifyGetArchivesStub, "Stub of getting archives was not called!")

	actualUser, err := getUserFromDb(dynamodbClient, downloader, username)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedUser := UserRecord{
		UserId:   userId,
		Username: username,
	}

	assert.Equal(t, expectedUser, actualUser, fmt.Sprintf("User %v is not present in %v table!", username, downloader.usersTableName))

	archiveId_2021_10 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/10", username)
	archive_2021_10, err := getArchiveFromDb(dynamodbClient, downloader, userId, archiveId_2021_10)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedArchive_2021_10 := ArchiveRecord{
		UserId:         userId,
		ArchiveId:      archiveId_2021_10,
		Resource:       archiveId_2021_10,
		Till:           time.Date(2021, 11, 1, 0, 0, 0, 0, time.UTC),
		LastGamePlayed: nil,
		Downloaded:     0,
		Status:         NotDownloaded,
	}

	assert.Equal(t, expectedArchive_2021_10, archive_2021_10, fmt.Sprintf("Archive %v is not present in %v table!", archiveId_2021_10, downloader.archivesTableName))

	archiveId_2021_11 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/11", username)
	archive_2021_11, err := getArchiveFromDb(dynamodbClient, downloader, userId, archiveId_2021_11)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedArchive_2021_11 := ArchiveRecord{
		UserId:         userId,
		ArchiveId:      archiveId_2021_11,
		Resource:       archiveId_2021_11,
		Till:           time.Date(2021, 12, 1, 0, 0, 0, 0, time.UTC),
		LastGamePlayed: nil,
		Downloaded:     0,
		Status:         NotDownloaded,
	}

	assert.Equal(t, expectedArchive_2021_11, archive_2021_11, fmt.Sprintf("Archive %v is not present in %v table!", archiveId_2021_11, downloader.archivesTableName))

	archiveId_2021_12 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/12", username)
	archive_2021_12, err := getArchiveFromDb(dynamodbClient, downloader, userId, archiveId_2021_12)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedArchive_2021_12 := ArchiveRecord{
		UserId:         userId,
		ArchiveId:      archiveId_2021_12,
		Resource:       archiveId_2021_12,
		Till:           time.Date(2022, 1, 1, 0, 0, 0, 0, time.UTC),
		LastGamePlayed: nil,
		Downloaded:     0,
		Status:         NotDownloaded,
	}

	assert.Equal(t, expectedArchive_2021_12, archive_2021_12, fmt.Sprintf("Archive %v is not present in %v table!", archiveId_2021_12, downloader.archivesTableName))

	actualCommands, err := getCommands(svc, downloader)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	command_2021_10 := DownloadGameCommand{
		UserName:          username,
		Platform:          "CHESS_DOT_COM",
		UserId:            userId,
		ArchiveId:         archiveId_2021_10,
		DownloadRequestId: downloadRequestId,
	}

	command_2021_11 := DownloadGameCommand{
		UserName:          username,
		Platform:          "CHESS_DOT_COM",
		UserId:            userId,
		ArchiveId:         archiveId_2021_11,
		DownloadRequestId: downloadRequestId,
	}

	command_2021_12 := DownloadGameCommand{
		UserName:          username,
		Platform:          "CHESS_DOT_COM",
		UserId:            userId,
		ArchiveId:         archiveId_2021_12,
		DownloadRequestId: downloadRequestId,
	}

	expectedCommands := []DownloadGameCommand{
		command_2021_10,
		command_2021_11,
		command_2021_12,
	}

	assert.Equal(t, expectedCommands, actualCommands, "Commands are not equal!")

}

func Test_ArchiveDownloader_should_emit_DownloadGameCommands_for_partially_downloaded_archives(t *testing.T) {
	awsConfig := aws.Config{
		Region:     aws.String("us-east-1"),
		Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
		DisableSSL: aws.Bool(true),
	}
	downloader := ArchiveDownloader{
		downloadGamesQueueUrl:    "http://localhost:4566/000000000000/DownloadGames.fifo",
		chessDotComUrl:           "http://0.0.0.0:18443",
		usersTableName:           "users",
		archivesTableName:        "archives",
		downloadRequestTableName: "tasks",
		awsConfig:                &awsConfig,
	}

	awsSession, err := session.NewSession(downloader.awsConfig)
	if err != nil {
		assert.Fail(t, "failed to establish aws session!")
		return
	}

	dynamodbClient := dynamodb.New(awsSession)
	svc := sqs.New(awsSession)

	wiremockClient := wiremock.NewClient("http://0.0.0.0:18443")
	defer wiremockClient.Reset()

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)

	archiveId_2021_10 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/10", username)

	existingArchive_2021_10 := ArchiveRecord{
		UserId:         userId,
		ArchiveId:      archiveId_2021_10,
		Resource:       archiveId_2021_10,
		Till:           time.Date(2021, 11, 1, 0, 0, 0, 0, time.UTC),
		LastGamePlayed: nil,
		Downloaded:     0,
		Status:         NotDownloaded,
	}

	err = putArchive(dynamodbClient, downloader, existingArchive_2021_10)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	archiveId_2021_11 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/11", username)
	var gamePlayedIn_2011_11 = "https://www.chess.com/game/live/52659611873"
	existingArchive_2021_11 := ArchiveRecord{
		UserId:         userId,
		ArchiveId:      archiveId_2021_11,
		Resource:       archiveId_2021_11,
		Till:           time.Date(2021, 12, 1, 0, 0, 0, 0, time.UTC),
		LastGamePlayed: &gamePlayedIn_2011_11,
		Downloaded:     15,
		Status:         PartiallyDownloaded,
	}

	err = putArchive(dynamodbClient, downloader, existingArchive_2021_11)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	archiveId_2021_12 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/12", username)
	var gamePlayedIn_2011_12 = "https://www.chess.com/game/live/52659611874"

	existingArchive_2021_12 := ArchiveRecord{
		UserId:         userId,
		ArchiveId:      archiveId_2021_12,
		Resource:       archiveId_2021_12,
		Till:           time.Date(2022, 1, 1, 0, 0, 0, 0, time.UTC),
		LastGamePlayed: &gamePlayedIn_2011_12,
		Downloaded:     1111,
		Status:         FullyDownloaded,
	}

	err = putArchive(dynamodbClient, downloader, existingArchive_2021_12)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	usersProfileReponseBody := fmt.Sprintf(
		`{
      "player_id": 191338281,
      "@id": "%v",
      "url": "https://www.chess.com/member/%v",
      "username": "%v",
      "followers": 10,
      "country": "https://api.chess.com/pub/country/AM",
      "last_online": 1678264516,
      "joined": 1658920370,
      "status": "premium",
      "is_streamer": false,
      "verified": false,
      "league": "Champion"
    }`,
		userId,
		username,
		username,
	)

	getUsersProfileStub := wiremock.Get(wiremock.URLPathEqualTo(fmt.Sprintf("/pub/player/%v", username))).
		WillReturnResponse(
			wiremock.NewResponse().
				WithBody(usersProfileReponseBody).
				WithHeader("Content-Type", "application/json").
				WithStatus(http.StatusOK),
		)
	err = wiremockClient.StubFor(getUsersProfileStub)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	archivesResponseBody := fmt.Sprintf(
		`{
			"archives": [
				"https://api.chess.com/pub/player/%v/games/2021/10",
				"https://api.chess.com/pub/player/%v/games/2021/11",
				"https://api.chess.com/pub/player/%v/games/2021/12"
			]
		}`, username, username, username,
	)

	getArchivesStub := wiremock.Get(wiremock.URLPathEqualTo(fmt.Sprintf("/pub/player/%v/games/archives", username))).
		WillReturnResponse(
			wiremock.NewResponse().
				WithBody(archivesResponseBody).
				WithHeader("Content-Type", "application/json").
				WithStatus(http.StatusOK),
		)
	err = wiremockClient.StubFor(getArchivesStub)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"user":"%v"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/game",
			},
		},
	}

	actualResponse, err := downloader.DownloadArchiveAndDistributeDonwloadGameCommands(&event)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}
	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualDownloadResponse := DownloadResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualDownloadResponse)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	downloadRequestId := actualDownloadResponse.TaskId

	downloadRequestStatus, err := getDownloadStatusFromDb(dynamodbClient, downloader, downloadRequestId)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedDownloadRequestStatus := DownloadStatusRecord{
		DownloadRequestId: downloadRequestId,
		Failed:            0,
		Succeed:           0,
		Done:              0,
		Pending:           2,
		Total:             2,
	}

	assert.Equal(t, expectedDownloadRequestStatus, downloadRequestStatus, "Download request status is not equal!")

	verifyGetUsersProfileStub, err := wiremockClient.Verify(getUsersProfileStub.Request(), 1)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}
	assert.Equal(t, true, verifyGetUsersProfileStub, fmt.Sprintf("Stub of getting user profile %v was not called!", username))

	verifyGetArchivesStub, err := wiremockClient.Verify(getArchivesStub.Request(), 1)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	assert.Equal(t, true, verifyGetArchivesStub, "Stub of getting archives was not called!")

	actualUser, err := getUserFromDb(dynamodbClient, downloader, username)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	expectedUser := UserRecord{
		UserId:   userId,
		Username: username,
	}

	assert.Equal(t, expectedUser, actualUser, fmt.Sprintf("User %v is not present in %v table!", username, downloader.usersTableName))

	actualCommands, err := getCommands(svc, downloader)
	if err != nil {
		assert.FailNow(t, fmt.Sprintf("%v", err.Error()))
	}

	command_2021_10 := DownloadGameCommand{
		UserName:          username,
		Platform:          "CHESS_DOT_COM",
		UserId:            userId,
		ArchiveId:         archiveId_2021_10,
		DownloadRequestId: downloadRequestId,
	}

	command_2021_11 := DownloadGameCommand{
		UserName:          username,
		Platform:          "CHESS_DOT_COM",
		UserId:            userId,
		ArchiveId:         archiveId_2021_11,
		DownloadRequestId: downloadRequestId,
	}

	expectedCommands := []DownloadGameCommand{
		command_2021_10,
		command_2021_11,
	}

	assert.Equal(t, expectedCommands, actualCommands, "Commands are not equal!")

}

func getUserFromDb(dynamodbClient dynamodbiface.DynamoDBAPI, archviveDownloader ArchiveDownloader, userId string) (user UserRecord, err error) {

	fetchUserRequest := &dynamodb.GetItemInput{
		TableName: aws.String(archviveDownloader.usersTableName),
		Key: map[string]*dynamodb.AttributeValue{
			"user_name": {
				S: aws.String(userId),
			},
			"platform": {
				S: aws.String("CHESS_DOT_COM"),
			},
		},
	}

	userItem, err := dynamodbClient.GetItem(fetchUserRequest)
	if err != nil {
		return
	}

	err = dynamodbattribute.UnmarshalMap(userItem.Item, &user)
	if err != nil {
		return
	}

	return
}

func getArchiveFromDb(dynamodbClient dynamodbiface.DynamoDBAPI, downloader ArchiveDownloader, userId string, archiveId string) (archive ArchiveRecord, err error) {

	fetchArchiveRequest := &dynamodb.GetItemInput{
		TableName: aws.String(downloader.archivesTableName),
		Key: map[string]*dynamodb.AttributeValue{
			"user_id": {
				S: aws.String(userId),
			},
			"archive_id": {
				S: aws.String(archiveId),
			},
		},
	}

	archiveItem, err := dynamodbClient.GetItem(fetchArchiveRequest)
	if err != nil {
		return
	}

	err = dynamodbattribute.UnmarshalMap(archiveItem.Item, &archive)
	if err != nil {
		return
	}

	return
}

func getDownloadStatusFromDb(dynamodbClient dynamodbiface.DynamoDBAPI, downloader ArchiveDownloader, downloadRequestId string) (downloadStatus DownloadStatusRecord, err error) {

	fetchDownloadStatusRequest := &dynamodb.GetItemInput{
		TableName: aws.String(downloader.downloadRequestTableName),
		Key: map[string]*dynamodb.AttributeValue{
			"task_id": {
				S: aws.String(downloadRequestId),
			},
		},
	}

	downloadStatusItem, err := dynamodbClient.GetItem(fetchDownloadStatusRequest)
	if err != nil {
		return
	}

	err = dynamodbattribute.UnmarshalMap(downloadStatusItem.Item, &downloadStatus)
	if err != nil {
		return
	}

	return
}

func putArchive(dynamodbClient dynamodbiface.DynamoDBAPI, downloader ArchiveDownloader, archive ArchiveRecord) (err error) {

	archiveItem, err := dynamodbattribute.MarshalMap(archive)
	if err != nil {
		return
	}

	putArchiveRequest := &dynamodb.PutItemInput{
		TableName: aws.String(downloader.archivesTableName),
		Item:      archiveItem,
	}

	_, err = dynamodbClient.PutItem(putArchiveRequest)
	if err != nil {
		return
	}

	return
}

func getCommands(svc *sqs.SQS, archviveDownloader ArchiveDownloader) (commands []DownloadGameCommand, err error) {
	resp, err := svc.ReceiveMessage(&sqs.ReceiveMessageInput{
		QueueUrl:            &archviveDownloader.downloadGamesQueueUrl,
		MaxNumberOfMessages: aws.Int64(10), // You can adjust this number
		VisibilityTimeout:   aws.Int64(30), // 30 seconds timeout for processing
		WaitTimeSeconds:     aws.Int64(0),  // Long polling
	})
	if err != nil {
		fmt.Printf("Failed to fetch message with error%v", err)
		return
	}
	for _, message := range resp.Messages {
		_, err = svc.DeleteMessage(&sqs.DeleteMessageInput{
			QueueUrl:      &archviveDownloader.downloadGamesQueueUrl,
			ReceiptHandle: message.ReceiptHandle,
		})
		if err != nil {
			fmt.Printf("Failed to delete message with error%v", err)
			return
		}
		body := message.Body
		command := DownloadGameCommand{}
		err = json.Unmarshal([]byte(*body), &command)
		if err != nil {
			fmt.Printf("Failed to unmarshal message with error%v", err)
			return
		}

		commands = append(commands, command)
	}
	return
}
