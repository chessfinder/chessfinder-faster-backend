package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
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
	downloadGamesQueueUrl:                    "http://localhost:4566/000000000000/chessfinder_sqs-DownloadGames.fifo",
	chessDotComUrl:                           "http://0.0.0.0:18443",
	usersTableName:                           "chessfinder_dynamodb-users",
	archivesTableName:                        "chessfinder_dynamodb-archives",
	downloadsTableName:                       "chessfinder_dynamodb-downloads",
	downloadsByConsistentDownloadIdIndexName: "chessfinder_dynamodb-downloadsByConsistentDownloadId",
	downloadInfoExpiresIn:                    24 * time.Hour,
	awsConfig:                                &awsConfig,
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

func Test_ArchiveDownloader_should_emit_DownloadGameCommands_for_all_archives_for_a_new_user(t *testing.T) {
	var err error
	startOfTest := time.Now()

	err = deleteAllDownloads()
	assert.NoError(t, err)

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

	actualResponse, err := downloader.DownloadArchiveAndDistributeDownloadGameCommands(&event)
	assert.NoError(t, err)

	startOfCheck := time.Now()

	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualDownloadResponse := DownloadResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualDownloadResponse)
	assert.NoError(t, err)

	downloadId := actualDownloadResponse.DownloadId

	actualDownloadRecord, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownloadRecord)

	expectedConsistentDownloadId := downloads.NewConsistentDownloadId(userId)

	assert.Equal(t, expectedConsistentDownloadId, actualDownloadRecord.ConsistentDownloadId)
	assert.Equal(t, 0, actualDownloadRecord.Failed)
	assert.Equal(t, 0, actualDownloadRecord.Succeed)
	assert.Equal(t, 0, actualDownloadRecord.Done)
	assert.Equal(t, 3, actualDownloadRecord.Pending)
	assert.Equal(t, 3, actualDownloadRecord.Total)
	assert.True(t, startOfTest.Before(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfTest.Before(actualDownloadRecord.LastDownloadedAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.LastDownloadedAt.ToTime()))

	assert.True(t, startOfTest.Add(downloader.downloadInfoExpiresIn-time.Second).Before(time.Time(actualDownloadRecord.ExpiresAt)))
	assert.True(t, startOfCheck.Add(downloader.downloadInfoExpiresIn+time.Second).After(time.Time(actualDownloadRecord.ExpiresAt)))

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
		UserId:              userId,
		Platform:            "CHESS_DOT_COM",
		Username:            username,
		DownloadFromScratch: false,
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

	lastThreeCommands, err := queue.GetLastNCommands(svc, downloader.downloadGamesQueueUrl, 3)
	assert.NoError(t, err)

	actualCommands := make([]queue.DownloadGamesCommand, len(lastThreeCommands))
	for i, message := range lastThreeCommands {
		var command queue.DownloadGamesCommand
		err = json.Unmarshal([]byte(*message.Body), &command)
		if err != nil {
			return
		}
		actualCommands[i] = command
	}
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

func Test_ArchiveDownloader_should_emit_DownloadGameCommands_for_all_archives_as_a_process_from_scratch_for_a_new_user(t *testing.T) {
	var err error
	defer wiremockClient.Reset()

	err = deleteAllDownloads()
	assert.NoError(t, err)

	startOfTest := time.Now()

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)

	newUserRecord := users.UserRecord{
		UserId:              userId,
		Platform:            "CHESS_DOT_COM",
		Username:            username,
		DownloadFromScratch: true,
	}

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

	actualResponse, err := downloader.DownloadArchiveAndDistributeDownloadGameCommands(&event)
	startOfCheck := time.Now()

	assert.NoError(t, err)
	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualDownloadResponse := DownloadResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualDownloadResponse)
	assert.NoError(t, err)

	downloadId := actualDownloadResponse.DownloadId

	actualDownloadRecord, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownloadRecord)

	expectedConsistentDownloadId := downloads.NewConsistentDownloadId(userId)

	assert.Equal(t, expectedConsistentDownloadId, actualDownloadRecord.ConsistentDownloadId)
	assert.Equal(t, 0, actualDownloadRecord.Failed)
	assert.Equal(t, 0, actualDownloadRecord.Succeed)
	assert.Equal(t, 0, actualDownloadRecord.Done)
	assert.Equal(t, 3, actualDownloadRecord.Pending)
	assert.Equal(t, 3, actualDownloadRecord.Total)
	assert.True(t, startOfTest.Before(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfTest.Before(actualDownloadRecord.LastDownloadedAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.LastDownloadedAt.ToTime()))
	assert.True(t, startOfTest.Add(downloader.downloadInfoExpiresIn-time.Second).Before(time.Time(actualDownloadRecord.ExpiresAt)))
	assert.True(t, startOfCheck.Add(downloader.downloadInfoExpiresIn+time.Second).After(time.Time(actualDownloadRecord.ExpiresAt)))

	verifyGetUsersProfileStub, err := wiremockClient.Verify(getUsersProfileStub.Request(), 1)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetUsersProfileStub, fmt.Sprintf("Stub of getting user profile %v was not called!", username))

	verifyGetArchivesStub, err := wiremockClient.Verify(getArchivesStub.Request(), 1)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetArchivesStub, "Stub of getting archives was not called!")

	actualUserRecord, err := usersTable.GetUserRecord(username, users.ChessDotCom)
	assert.NoError(t, err)
	assert.NotNil(t, actualUserRecord)

	expectedUserRecord := newUserRecord
	expectedUserRecord.DownloadFromScratch = false

	assert.Equal(t, expectedUserRecord, *actualUserRecord)

	actualArchive_2021_10, err := archivesTable.GetArchiveRecord(userId, archiveId_2021_10)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive_2021_10)

	expectedArchive_2021_10 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_10,
		Resource:     archiveId_2021_10,
		Year:         2021,
		Month:        10,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	assert.Equal(t, expectedArchive_2021_10, *actualArchive_2021_10)

	actualArchive_2021_11, err := archivesTable.GetArchiveRecord(userId, archiveId_2021_11)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive_2021_11)

	expectedArchive_2021_11 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_11,
		Resource:     archiveId_2021_11,
		Year:         2021,
		Month:        11,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	assert.Equal(t, expectedArchive_2021_11, *actualArchive_2021_11)

	actualArchive_2021_12, err := archivesTable.GetArchiveRecord(userId, archiveId_2021_12)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive_2021_12)

	expectedArchive_2021_12 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_12,
		Resource:     archiveId_2021_12,
		Year:         2021,
		Month:        12,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	assert.Equal(t, expectedArchive_2021_12, *actualArchive_2021_12, fmt.Sprintf("Archive %v is not present in %v table!", archiveId_2021_12, downloader.archivesTableName))

	lastThreeCommands, err := queue.GetLastNCommands(svc, downloader.downloadGamesQueueUrl, 3)
	assert.NoError(t, err)

	actualCommands := make([]queue.DownloadGamesCommand, len(lastThreeCommands))
	for i, message := range lastThreeCommands {
		var command queue.DownloadGamesCommand
		err = json.Unmarshal([]byte(*message.Body), &command)
		if err != nil {
			return
		}
		actualCommands[i] = command
	}
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

func Test_ArchiveDownloader_should_emit_DownloadGameCommands_for_all_archives_as_a_process_from_scratch_for_an_existing_user(t *testing.T) {
	var err error
	defer wiremockClient.Reset()

	err = deleteAllDownloads()
	assert.NoError(t, err)

	startOfTest := time.Now()

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)

	existingUserRecord := users.UserRecord{
		UserId:              userId,
		Platform:            "CHESS_DOT_COM",
		Username:            username,
		DownloadFromScratch: true,
	}

	err = usersTable.PutUserRecord(existingUserRecord)
	assert.NoError(t, err)

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

	actualResponse, err := downloader.DownloadArchiveAndDistributeDownloadGameCommands(&event)
	startOfCheck := time.Now()

	assert.NoError(t, err)
	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualDownloadResponse := DownloadResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualDownloadResponse)
	assert.NoError(t, err)

	downloadId := actualDownloadResponse.DownloadId

	actualDownloadRecord, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownloadRecord)

	expectedConsistentDownloadId := downloads.NewConsistentDownloadId(userId)

	assert.Equal(t, expectedConsistentDownloadId, actualDownloadRecord.ConsistentDownloadId)
	assert.Equal(t, 0, actualDownloadRecord.Failed)
	assert.Equal(t, 0, actualDownloadRecord.Succeed)
	assert.Equal(t, 0, actualDownloadRecord.Done)
	assert.Equal(t, 3, actualDownloadRecord.Pending)
	assert.Equal(t, 3, actualDownloadRecord.Total)
	assert.True(t, startOfTest.Before(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfTest.Before(actualDownloadRecord.LastDownloadedAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.LastDownloadedAt.ToTime()))
	assert.True(t, startOfTest.Add(downloader.downloadInfoExpiresIn-time.Second).Before(time.Time(actualDownloadRecord.ExpiresAt)))
	assert.True(t, startOfCheck.Add(downloader.downloadInfoExpiresIn+time.Second).After(time.Time(actualDownloadRecord.ExpiresAt)))

	verifyGetUsersProfileStub, err := wiremockClient.Verify(getUsersProfileStub.Request(), 0)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetUsersProfileStub, fmt.Sprintf("Stub of getting user profile %v was not called!", username))

	verifyGetArchivesStub, err := wiremockClient.Verify(getArchivesStub.Request(), 1)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetArchivesStub, "Stub of getting archives was not called!")

	actualUserRecord, err := usersTable.GetUserRecord(username, users.ChessDotCom)
	assert.NoError(t, err)
	assert.NotNil(t, actualUserRecord)

	expectedUserRecord := existingUserRecord
	expectedUserRecord.DownloadFromScratch = false

	assert.Equal(t, expectedUserRecord, *actualUserRecord)

	actualArchive_2021_10, err := archivesTable.GetArchiveRecord(userId, archiveId_2021_10)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive_2021_10)

	expectedArchive_2021_10 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_10,
		Resource:     archiveId_2021_10,
		Year:         2021,
		Month:        10,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	assert.Equal(t, expectedArchive_2021_10, *actualArchive_2021_10)

	actualArchive_2021_11, err := archivesTable.GetArchiveRecord(userId, archiveId_2021_11)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive_2021_11)

	expectedArchive_2021_11 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_11,
		Resource:     archiveId_2021_11,
		Year:         2021,
		Month:        11,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	assert.Equal(t, expectedArchive_2021_11, *actualArchive_2021_11)

	actualArchive_2021_12, err := archivesTable.GetArchiveRecord(userId, archiveId_2021_12)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive_2021_12)

	expectedArchive_2021_12 := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId_2021_12,
		Resource:     archiveId_2021_12,
		Year:         2021,
		Month:        12,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	assert.Equal(t, expectedArchive_2021_12, *actualArchive_2021_12, fmt.Sprintf("Archive %v is not present in %v table!", archiveId_2021_12, downloader.archivesTableName))

	lastThreeCommands, err := queue.GetLastNCommands(svc, downloader.downloadGamesQueueUrl, 3)
	assert.NoError(t, err)

	actualCommands := make([]queue.DownloadGamesCommand, len(lastThreeCommands))
	for i, message := range lastThreeCommands {
		var command queue.DownloadGamesCommand
		err = json.Unmarshal([]byte(*message.Body), &command)
		if err != nil {
			return
		}
		actualCommands[i] = command
	}
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

func Test_ArchiveDownloader_should_emit_DownloadGameCommands_for_all_missing_archives_as_continuation_for_the_previous_downloading_process(t *testing.T) {
	var err error
	defer wiremockClient.Reset()

	err = deleteAllDownloads()
	assert.NoError(t, err)

	startOfTest := time.Now()

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)

	newUserRecord := users.UserRecord{
		UserId:              userId,
		Platform:            "CHESS_DOT_COM",
		Username:            username,
		DownloadFromScratch: false,
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

	actualResponse, err := downloader.DownloadArchiveAndDistributeDownloadGameCommands(&event)
	startOfCheck := time.Now()

	assert.NoError(t, err)

	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualDownloadResponse := DownloadResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualDownloadResponse)
	assert.NoError(t, err)

	downloadId := actualDownloadResponse.DownloadId

	actualDownloadRecord, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownloadRecord)

	expectedConsistentDownloadId := downloads.NewConsistentDownloadId(userId)

	assert.Equal(t, expectedConsistentDownloadId, actualDownloadRecord.ConsistentDownloadId)
	assert.Equal(t, 0, actualDownloadRecord.Failed)
	assert.Equal(t, 0, actualDownloadRecord.Succeed)
	assert.Equal(t, 0, actualDownloadRecord.Done)
	assert.Equal(t, 3, actualDownloadRecord.Pending)
	assert.Equal(t, 3, actualDownloadRecord.Total)
	assert.True(t, startOfTest.Before(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfTest.Before(actualDownloadRecord.LastDownloadedAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.LastDownloadedAt.ToTime()))
	assert.True(t, startOfTest.Add(downloader.downloadInfoExpiresIn-time.Second).Before(time.Time(actualDownloadRecord.ExpiresAt)))
	assert.True(t, startOfCheck.Add(downloader.downloadInfoExpiresIn+time.Second).After(time.Time(actualDownloadRecord.ExpiresAt)))

	verifyGetUsersProfileStub, err := wiremockClient.Verify(getUsersProfileStub.Request(), 1)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetUsersProfileStub, fmt.Sprintf("Stub of getting user profile %v was not called!", username))

	verifyGetArchivesStub, err := wiremockClient.Verify(getArchivesStub.Request(), 1)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetArchivesStub, "Stub of getting archives was not called!")

	actualUserRecord, err := usersTable.GetUserRecord(username, users.ChessDotCom)
	assert.NoError(t, err)
	assert.NotNil(t, actualUserRecord)

	expectedUserRecord := newUserRecord

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

	lastThreeCommands, err := queue.GetLastNCommands(svc, downloader.downloadGamesQueueUrl, 3)
	assert.NoError(t, err)

	actualCommands := make([]queue.DownloadGamesCommand, len(lastThreeCommands))
	for i, message := range lastThreeCommands {
		var command queue.DownloadGamesCommand
		err = json.Unmarshal([]byte(*message.Body), &command)
		if err != nil {
			return
		}
		actualCommands[i] = command
	}
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

func Test_ArchiveDownloader_should_ignore_previous_download_process_if_if_is_almost_expired_and_start_downloading(t *testing.T) {
	var err error
	startOfTest := time.Now()

	err = deleteAllDownloads()
	assert.NoError(t, err)

	defer wiremockClient.Reset()

	username := strings.ToLower(uuid.New().String())
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)

	existingUserRecord := users.UserRecord{
		UserId:              userId,
		Platform:            users.ChessDotCom,
		Username:            username,
		DownloadFromScratch: false,
	}

	err = usersTable.PutUserRecord(existingUserRecord)
	assert.NoError(t, err)

	previousDownloadId := uuid.New().String()
	previouseDownloadExpiresAt := startOfTest.Add(5 * time.Second)
	previousDownload := downloads.DownloadRecord{
		ConsistentDownloadId: downloads.NewConsistentDownloadId(userId),
		DownloadId:           previousDownloadId,
		Failed:               0,
		Succeed:              0,
		Done:                 0,
		Pending:              3,
		Total:                3,
		StartAt:              db.Zuludatetime(startOfTest.Add(-downloader.downloadInfoExpiresIn - time.Second)),
		LastDownloadedAt:     db.Zuludatetime(startOfTest.Add(-downloader.downloadInfoExpiresIn - time.Second)),
		ExpiresAt:            dynamodbattribute.UnixTime(previouseDownloadExpiresAt),
	}
	err = downloadsTable.PutDownloadRecord(previousDownload)
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

	actualResponse, err := downloader.DownloadArchiveAndDistributeDownloadGameCommands(&event)
	assert.NoError(t, err)

	startOfCheck := time.Now()

	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualDownloadResponse := DownloadResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualDownloadResponse)
	assert.NoError(t, err)

	downloadId := actualDownloadResponse.DownloadId

	actualDownloadRecord, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownloadRecord)

	expectedConsistentDownloadId := downloads.NewConsistentDownloadId(userId)

	assert.Equal(t, expectedConsistentDownloadId, actualDownloadRecord.ConsistentDownloadId)
	assert.Equal(t, 0, actualDownloadRecord.Failed)
	assert.Equal(t, 0, actualDownloadRecord.Succeed)
	assert.Equal(t, 0, actualDownloadRecord.Done)
	assert.Equal(t, 3, actualDownloadRecord.Pending)
	assert.Equal(t, 3, actualDownloadRecord.Total)
	assert.True(t, startOfTest.Before(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfTest.Before(actualDownloadRecord.LastDownloadedAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.LastDownloadedAt.ToTime()))

	assert.True(t, startOfTest.Add(downloader.downloadInfoExpiresIn-time.Second).Before(time.Time(actualDownloadRecord.ExpiresAt)))
	assert.True(t, startOfCheck.Add(downloader.downloadInfoExpiresIn+time.Second).After(time.Time(actualDownloadRecord.ExpiresAt)))

	verifyGetUsersProfileStub, err := wiremockClient.Verify(getUsersProfileStub.Request(), 0)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetUsersProfileStub, fmt.Sprintf("Stub of getting user profile %v was not called!", username))

	verifyGetArchivesStub, err := wiremockClient.Verify(getArchivesStub.Request(), 1)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetArchivesStub, "Stub of getting archives was not called!")

	actualUserRecord, err := usersTable.GetUserRecord(username, users.ChessDotCom)
	assert.NoError(t, err)
	assert.NotNil(t, actualUserRecord)

	expectedUserRecord := users.UserRecord{
		UserId:              userId,
		Platform:            "CHESS_DOT_COM",
		Username:            username,
		DownloadFromScratch: false,
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

	lastThreeCommands, err := queue.GetLastNCommands(svc, downloader.downloadGamesQueueUrl, 3)
	assert.NoError(t, err)

	actualCommands := make([]queue.DownloadGamesCommand, len(lastThreeCommands))
	for i, message := range lastThreeCommands {
		var command queue.DownloadGamesCommand
		err = json.Unmarshal([]byte(*message.Body), &command)
		if err != nil {
			return
		}
		actualCommands[i] = command
	}
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

func Test_ArchiveDownloader_should_return_id_of_the_previous_download_process_and_never_start_downloading(t *testing.T) {
	var err error
	startOfTest := time.Now()

	err = deleteAllDownloads()
	assert.NoError(t, err)

	defer wiremockClient.Reset()

	username := strings.ToLower(uuid.New().String())
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)

	existingUserRecord := users.UserRecord{
		UserId:              userId,
		Platform:            users.ChessDotCom,
		Username:            username,
		DownloadFromScratch: false,
	}

	err = usersTable.PutUserRecord(existingUserRecord)
	assert.NoError(t, err)

	previousDownloadId := uuid.New().String()
	previouseDownloadExpiresAt := startOfTest.Add(25 * time.Second)
	previousDownload := downloads.DownloadRecord{
		ConsistentDownloadId: downloads.NewConsistentDownloadId(userId),
		DownloadId:           previousDownloadId,
		Failed:               0,
		Succeed:              0,
		Done:                 0,
		Pending:              3,
		Total:                3,
		StartAt:              db.Zuludatetime(startOfTest.Add(-downloader.downloadInfoExpiresIn - time.Second)),
		LastDownloadedAt:     db.Zuludatetime(startOfTest.Add(-downloader.downloadInfoExpiresIn - time.Second)),
		ExpiresAt:            dynamodbattribute.UnixTime(previouseDownloadExpiresAt),
	}

	err = downloadsTable.PutDownloadRecord(previousDownload)
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

	actualResponse, err := downloader.DownloadArchiveAndDistributeDownloadGameCommands(&event)
	assert.NoError(t, err)

	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualDownloadResponse := DownloadResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualDownloadResponse)
	assert.NoError(t, err)

	downloadId := actualDownloadResponse.DownloadId

	actualDownloadRecord, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownloadRecord)

	assert.Equal(t, previousDownload.ConsistentDownloadId, actualDownloadRecord.ConsistentDownloadId)
	assert.Equal(t, previousDownload.DownloadId, actualDownloadRecord.DownloadId)
	assert.Equal(t, previousDownload.Failed, actualDownloadRecord.Failed)
	assert.Equal(t, previousDownload.Succeed, actualDownloadRecord.Succeed)
	assert.Equal(t, previousDownload.Done, actualDownloadRecord.Done)
	assert.Equal(t, previousDownload.Pending, actualDownloadRecord.Pending)
	assert.Equal(t, previousDownload.Total, actualDownloadRecord.Total)
	assert.Equal(t, previousDownload.StartAt.String(), actualDownloadRecord.StartAt.String())
	assert.Equal(t, previousDownload.LastDownloadedAt.String(), actualDownloadRecord.LastDownloadedAt.String())
	assert.Equal(t, time.Time(previousDownload.ExpiresAt).Unix(), time.Time(actualDownloadRecord.ExpiresAt).Unix())

	verifyGetUsersProfileStub, err := wiremockClient.Verify(getUsersProfileStub.Request(), 0)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetUsersProfileStub, fmt.Sprintf("Stub of getting user profile %v was not called!", username))

	verifyGetArchivesStub, err := wiremockClient.Verify(getArchivesStub.Request(), 0)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetArchivesStub, "Stub of getting archives was not called!")

}

func Test_ArchiveDownloader_should_emit_DownloadGameCommands_for_partially_downloaded_archives_as_continuation_for_the_previous_downloading_process(t *testing.T) {
	var err error

	err = deleteAllDownloads()
	assert.NoError(t, err)

	defer wiremockClient.Reset()

	startOfTest := time.Now()

	username := uuid.New().String()
	userId := fmt.Sprintf("https://api.chess.com/pub/player/%v", username)

	existingUserRecord := users.UserRecord{
		UserId:              userId,
		Platform:            "CHESS_DOT_COM",
		Username:            username,
		DownloadFromScratch: false,
	}

	err = usersTable.PutUserRecord(existingUserRecord)
	assert.NoError(t, err)

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

	actualResponse, err := downloader.DownloadArchiveAndDistributeDownloadGameCommands(&event)
	startOfCheck := time.Now()

	assert.NoError(t, err)
	assert.Equal(t, http.StatusOK, actualResponse.StatusCode, "Response status code is not 200!")

	actualDownloadResponse := DownloadResponse{}
	err = json.Unmarshal([]byte(actualResponse.Body), &actualDownloadResponse)
	assert.NoError(t, err)

	downloadId := actualDownloadResponse.DownloadId

	actualDownloadRecord, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownloadRecord)

	expectedConsistentDownloadId := downloads.NewConsistentDownloadId(userId)

	assert.Equal(t, expectedConsistentDownloadId, actualDownloadRecord.ConsistentDownloadId)
	assert.Equal(t, 0, actualDownloadRecord.Failed)
	assert.Equal(t, 0, actualDownloadRecord.Succeed)
	assert.Equal(t, 0, actualDownloadRecord.Done)
	assert.Equal(t, 2, actualDownloadRecord.Pending)
	assert.Equal(t, 2, actualDownloadRecord.Total)
	assert.True(t, startOfTest.Before(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.StartAt.ToTime()))
	assert.True(t, startOfTest.Before(actualDownloadRecord.LastDownloadedAt.ToTime()))
	assert.True(t, startOfCheck.After(actualDownloadRecord.LastDownloadedAt.ToTime()))
	assert.True(t, startOfTest.Add(downloader.downloadInfoExpiresIn-time.Second).Before(time.Time(actualDownloadRecord.ExpiresAt)))
	assert.True(t, startOfCheck.Add(downloader.downloadInfoExpiresIn+time.Second).After(time.Time(actualDownloadRecord.ExpiresAt)))

	verifyGetUsersProfileStub, err := wiremockClient.Verify(getUsersProfileStub.Request(), 0)
	assert.NoError(t, err)
	assert.Equal(t, true, verifyGetUsersProfileStub, fmt.Sprintf("Stub of getting user profile %v was not called!", username))

	verifyGetArchivesStub, err := wiremockClient.Verify(getArchivesStub.Request(), 1)
	assert.NoError(t, err)

	assert.Equal(t, true, verifyGetArchivesStub, "Stub of getting archives was not called!")

	actualUserRecord, err := usersTable.GetUserRecord(username, users.ChessDotCom)
	assert.NoError(t, err)
	assert.NotNil(t, actualUserRecord)

	expectedUser := existingUserRecord

	assert.Equal(t, expectedUser, *actualUserRecord)

	lastTwoCommands, err := queue.GetLastNCommands(svc, downloader.downloadGamesQueueUrl, 2)
	assert.NoError(t, err)

	actualCommands := make([]queue.DownloadGamesCommand, len(lastTwoCommands))
	for i, message := range lastTwoCommands {
		var command queue.DownloadGamesCommand
		err = json.Unmarshal([]byte(*message.Body), &command)
		if err != nil {
			return
		}
		actualCommands[i] = command
	}
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

func deleteAllDownloads() (err error) {
	output, err := dynamodbClient.Scan(&dynamodb.ScanInput{
		TableName: aws.String(downloader.downloadsTableName),
	})
	if err != nil {
		return
	}
	for _, item := range output.Items {
		_, err = dynamodbClient.DeleteItem(&dynamodb.DeleteItemInput{
			TableName: aws.String(downloader.downloadsTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"download_id": {
					S: item["download_id"].S,
				},
			},
		})
		if err != nil {
			return
		}
	}
	return
}
