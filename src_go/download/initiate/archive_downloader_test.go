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
	"github.com/aws/aws-sdk-go/service/sqs"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/archives"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/downloads"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/users"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/wiremock/go-wiremock"
)

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

var downloader = ArchiveDownloader{
	downloadGamesQueueUrl: "http://localhost:4566/000000000000/chessfinder_sqs-DownloadGames.fifo",
	chessDotComUrl:        "http://0.0.0.0:18443",
	usersTableName:        "chessfinder_dynamodb-users",
	archivesTableName:     "chessfinder_dynamodb-archives",
	downloadsTableName:    "chessfinder_dynamodb-downloads",
	awsConfig:             &awsConfig,
}

var awsSession = session.Must(session.NewSession(&awsConfig))

var dynamodbClient = dynamodb.New(awsSession)
var svc = sqs.New(awsSession)

var wiremockClient = wiremock.NewClient("http://0.0.0.0:18443")

var usersTable = users.UsersTable{
	Name:           downloader.usersTableName,
	DynamodbClient: dynamodbClient,
}

var downloadsTable = downloads.DownloadsTable{
	Name:           downloader.downloadsTableName,
	DynamodbClient: dynamodbClient,
}

var archivesTable = archives.ArchivesTable{
	Name:           downloader.archivesTableName,
	DynamodbClient: dynamodbClient,
}

func Test_ArchiveDownloader_should_emit_DownloadGameCommands_for_all_missing_archives(t *testing.T) {
	var err error
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
	assert.NoError(t, err)

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
	assert.NoError(t, err)

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"username":"%v", "platform": "CHESS_DOT_COM"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/game",
			},
		},
	}

	actualResponse, err := downloader.DownloadArchiveAndDistributeDonwloadGameCommands(&event)
	assert.NoError(t, err)

	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualDownloadResponse := DownloadResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualDownloadResponse)
	assert.NoError(t, err)

	downloadId := actualDownloadResponse.DownloadId

	actualDownloadRecord, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownloadRecord)

	expectedDownloadRecord := downloads.DownloadRecord{
		DownloadId: downloadId,
		Failed:     0,
		Succeed:    0,
		Done:       0,
		Pending:    3,
		Total:      3,
	}

	assert.Equal(t, expectedDownloadRecord, *actualDownloadRecord, "Download request status is not equal!")

	verifyGetUsersProfileStub, err := wiremockClient.Verify(getUsersProfileStub.Request(), 1)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetUsersProfileStub, fmt.Sprintf("Stub of getting user profile %v was not called!", username))

	verifyGetArchivesStub, err := wiremockClient.Verify(getArchivesStub.Request(), 1)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetArchivesStub, "Stub of getting archives was not called!")

	actualUserRecord, err := usersTable.GetUserRecord(username, users.ChessDotCom)
	assert.NoError(t, err)
	assert.NotNil(t, actualUserRecord)

	expectedUserRecord := users.UserRecord{
		UserId:   userId,
		Platform: "CHESS_DOT_COM",
		Username: username,
	}

	assert.Equal(t, expectedUserRecord, *actualUserRecord)

	archiveId_2021_10 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/10", username)
	archive_2021_10, err := archivesTable.GetArchiveRecord(userId, archiveId_2021_10)
	assert.NoError(t, err)
	assert.NotNil(t, archive_2021_10)

	expectedArchive_2021_10 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_10,
		Resource:     archiveId_2021_10,
		Year:         2021,
		Month:        10,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	assert.Equal(t, expectedArchive_2021_10, *archive_2021_10)

	archiveId_2021_11 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/11", username)
	archive_2021_11, err := archivesTable.GetArchiveRecord(userId, archiveId_2021_11)
	assert.NoError(t, err)
	assert.NotNil(t, archive_2021_11)

	expectedArchive_2021_11 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_11,
		Resource:     archiveId_2021_11,
		Year:         2021,
		Month:        11,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	assert.Equal(t, expectedArchive_2021_11, *archive_2021_11)

	archiveId_2021_12 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/12", username)
	archive_2021_12, err := archivesTable.GetArchiveRecord(userId, archiveId_2021_12)
	assert.NoError(t, err)
	assert.NotNil(t, archive_2021_12)

	expectedArchive_2021_12 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_12,
		Resource:     archiveId_2021_12,
		Year:         2021,
		Month:        12,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	assert.Equal(t, expectedArchive_2021_12, *archive_2021_12, fmt.Sprintf("Archive %v is not present in %v table!", archiveId_2021_12, downloader.archivesTableName))

	actualCommands, err := downloader.getCommands()
	assert.NoError(t, err)

	command_2021_10 := queue.DownloadGamesCommand{
		Username:   username,
		Platform:   queue.Platform("CHESS_DOT_COM"),
		UserId:     userId,
		ArchiveId:  archiveId_2021_10,
		DownloadId: downloadId,
	}

	command_2021_11 := queue.DownloadGamesCommand{
		Username:   username,
		Platform:   queue.Platform("CHESS_DOT_COM"),
		UserId:     userId,
		ArchiveId:  archiveId_2021_11,
		DownloadId: downloadId,
	}

	command_2021_12 := queue.DownloadGamesCommand{
		Username:   username,
		Platform:   queue.Platform("CHESS_DOT_COM"),
		UserId:     userId,
		ArchiveId:  archiveId_2021_12,
		DownloadId: downloadId,
	}

	expectedCommands := []queue.DownloadGamesCommand{
		command_2021_10,
		command_2021_11,
		command_2021_12,
	}

	assert.Equal(t, expectedCommands, actualCommands, "Commands are not equal!")

}

func Test_ArchiveDownloader_should_emit_DownloadGameCommands_for_partially_downloaded_archives(t *testing.T) {
	var err error
	defer wiremockClient.Reset()

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)

	archiveId_2021_10 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/10", username)
	existingArchive_2021_10 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_10,
		Resource:     archiveId_2021_10,
		Year:         2021,
		Month:        10,
		Downloaded:   0,
		DownloadedAt: nil,
	}

	archiveId_2021_11 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/11", username)
	archive_2021_11_downloadedAt := db.Zuludatetime(time.Date(2021, 11, 1, 11, 30, 17, 123000000, time.UTC))
	existingArchive_2021_11 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_11,
		Resource:     archiveId_2021_11,
		Year:         2021,
		Month:        11,
		Downloaded:   15,
		DownloadedAt: &archive_2021_11_downloadedAt,
	}

	archiveId_2021_12 := fmt.Sprintf("https://api.chess.com/pub/player/%v/games/2021/12", username)
	archive_2021_12_downloadedAt := db.Zuludatetime(time.Date(2022, 1, 1, 11, 30, 17, 123000000, time.UTC))
	existingArchive_2021_12 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_12,
		Resource:     archiveId_2021_12,
		Year:         2021,
		Month:        12,
		Downloaded:   1111,
		DownloadedAt: &archive_2021_12_downloadedAt,
	}

	allArchives := []archives.ArchiveRecord{
		existingArchive_2021_10,
		existingArchive_2021_11,
		existingArchive_2021_12,
	}
	err = archivesTable.PutArchiveRecords(allArchives)
	assert.NoError(t, err)

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
	assert.NoError(t, err)

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
	assert.NoError(t, err)

	event := events.APIGatewayV2HTTPRequest{
		Body: fmt.Sprintf(`{"username":"%v", "platform": "CHESS_DOT_COM"}`, username),
		RequestContext: events.APIGatewayV2HTTPRequestContext{
			HTTP: events.APIGatewayV2HTTPRequestContextHTTPDescription{
				Method: "POST",
				Path:   "/api/faster/game",
			},
		},
	}

	actualResponse, err := downloader.DownloadArchiveAndDistributeDonwloadGameCommands(&event)
	assert.NoError(t, err)
	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualDownloadResponse := DownloadResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualDownloadResponse)
	assert.NoError(t, err)

	downloadId := actualDownloadResponse.DownloadId

	actualDownloadRecord, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownloadRecord)

	expectedDownloadRecord := downloads.DownloadRecord{
		DownloadId: downloadId,
		Failed:     0,
		Succeed:    0,
		Done:       0,
		Pending:    2,
		Total:      2,
	}

	assert.Equal(t, expectedDownloadRecord, *actualDownloadRecord)

	verifyGetUsersProfileStub, err := wiremockClient.Verify(getUsersProfileStub.Request(), 1)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetUsersProfileStub, fmt.Sprintf("Stub of getting user profile %v was not called!", username))

	verifyGetArchivesStub, err := wiremockClient.Verify(getArchivesStub.Request(), 1)
	assert.NoError(t, err)

	assert.Equal(t, true, verifyGetArchivesStub, "Stub of getting archives was not called!")

	actualUserRecord, err := usersTable.GetUserRecord(username, users.ChessDotCom)
	assert.NoError(t, err)
	assert.NotNil(t, actualUserRecord)

	expectedUser := users.UserRecord{
		UserId:   userId,
		Platform: "CHESS_DOT_COM",
		Username: username,
	}

	assert.Equal(t, expectedUser, *actualUserRecord)

	actualCommands, err := downloader.getCommands()
	assert.NoError(t, err)

	command_2021_10 := queue.DownloadGamesCommand{
		Username:   username,
		Platform:   queue.Platform("CHESS_DOT_COM"),
		UserId:     userId,
		ArchiveId:  archiveId_2021_10,
		DownloadId: downloadId,
	}

	command_2021_11 := queue.DownloadGamesCommand{
		Username:   username,
		Platform:   queue.Platform("CHESS_DOT_COM"),
		UserId:     userId,
		ArchiveId:  archiveId_2021_11,
		DownloadId: downloadId,
	}

	expectedCommands := []queue.DownloadGamesCommand{
		command_2021_10,
		command_2021_11,
	}

	assert.Equal(t, expectedCommands, actualCommands, "Commands are not equal!")

}

func (downloader ArchiveDownloader) getCommands() (commands []queue.DownloadGamesCommand, err error) {
	resp, err := svc.ReceiveMessage(&sqs.ReceiveMessageInput{
		QueueUrl:            &downloader.downloadGamesQueueUrl,
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
			QueueUrl:      &downloader.downloadGamesQueueUrl,
			ReceiptHandle: message.ReceiptHandle,
		})
		if err != nil {
			fmt.Printf("Failed to delete message with error%v", err)
			return
		}
		body := message.Body
		command := queue.DownloadGamesCommand{}
		err = json.Unmarshal([]byte(*body), &command)
		if err != nil {
			fmt.Printf("Failed to unmarshal message with error%v", err)
			return
		}

		commands = append(commands, command)
	}
	return
}
