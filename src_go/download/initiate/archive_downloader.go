package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/aws/aws-sdk-go/service/sqs"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/api"
	"github.com/google/uuid"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

type ArchiveDownloader struct {
	chessDotComUrl           string
	awsConfig                *aws.Config
	usersTableName           string
	archivesTableName        string
	downloadRequestTableName string
	downloadGamesQueueUrl    string
}

func (downloader ArchiveDownloader) DownloadArchiveAndDistributeDonwloadGameCommands(
	event *events.APIGatewayV2HTTPRequest,
) (responseEvent events.APIGatewayV2HTTPResponse, err error) {
	responseEvent, err = downloader.downloadArchiveAndDistributeDonwloadGameCommandsFastFail(event)
	if err != nil {
		switch err := err.(type) {
		case api.ApiError:
			responseEvent = err.ToResponseEvent()
			return responseEvent, nil
		default:
			panic(err)
		}
	}
	return

}

func (downloader *ArchiveDownloader) downloadArchiveAndDistributeDonwloadGameCommandsFastFail(
	event *events.APIGatewayV2HTTPRequest,
) (responseEvent events.APIGatewayV2HTTPResponse, err error) {
	config := zap.NewProductionConfig()
	config.OutputPaths = []string{"stdout"}
	timeEncoder := func(t time.Time, enc zapcore.PrimitiveArrayEncoder) {
		enc.AppendString(t.UTC().Format("2006-01-02T15:04:05.000Z"))
	} // Log to stdout
	config.EncoderConfig.EncodeTime = timeEncoder

	// Create the logger from the configuration
	logger, err := config.Build()
	if err != nil {
		panic(err)
	}
	logger = logger.With(zap.String("requestId", event.RequestContext.RequestID))
	defer logger.Sync()

	awsSession, err := session.NewSession(downloader.awsConfig)
	if err != nil {
		logger.Error("impossible to create an AWS session!")
		return
	}
	dynamodbClient := dynamodb.New(awsSession)
	svc := sqs.New(awsSession)
	chessDotComClient := &http.Client{}

	method := event.RequestContext.HTTP.Method
	path := event.RequestContext.HTTP.Path

	if path != "/api/faster/game" || method != "POST" {
		logger.Error("archive downloader is attached to a wrong route!")
		// fixme check if error is 500. if not consider panicing
		err = errors.New("not supported")
		return
	}
	downloadRequest := DownloadRequest{}
	err = json.Unmarshal([]byte(event.Body), &downloadRequest)
	if err != nil {
		logger.Error("impossible to unmarshal the request body!")
		err = InvalidBody
		return
	}

	profile, err := downloader.getAndPersistUser(dynamodbClient, chessDotComClient, logger, downloadRequest)
	if err != nil {
		return
	}

	archivesFromChessDotCom, err := downloader.getArchivesFromChessDotCom(logger, profile)
	if err != nil {
		return
	}

	archivesFromDb, err := downloader.getArchivesFromDb(dynamodbClient, logger, profile)
	if err != nil {
		return
	}

	missingArchiveUrls := resolveMissingArchives(archivesFromChessDotCom, archivesFromDb)
	missingArchives, err := downloader.persistMissingArchives(dynamodbClient, logger, profile, missingArchiveUrls)
	if err != nil {
		return
	}

	archivesToDownload := resolveArchivesToDownload(archivesFromDb)
	downloadRequestId := uuid.New().String()
	downloadRecord := NewDownloadStatusRecord(downloadRequestId, len(missingArchives)+len(archivesToDownload))
	_, err = dynamodbClient.PutItem(downloadRecord.toPutItem(downloader.downloadRequestTableName))
	if err != nil {
		logger.Error("impossible to persist the download record!", zap.Error(err))
		return
	}

	err = downloader.publishDownloadGameCommands(logger, svc, profile, downloadRecord, missingArchives, archivesToDownload)
	if err != nil {
		return
	}

	downloadResponse := DownloadResponse{
		TaskId: downloadRequestId,
	}

	jsonBody, err := json.Marshal(downloadResponse)
	if err != nil {
		logger.Error("impossible to marshal the download response!", zap.Error(err))
		return
	}

	responseEvent = events.APIGatewayV2HTTPResponse{
		StatusCode: 200,
		Body:       string(jsonBody),
	}

	return
}

func (downloader ArchiveDownloader) getAndPersistUser(
	dynamodbClient *dynamodb.DynamoDB,
	chessDotComClient *http.Client,
	logger *zap.Logger,
	downloadRequest DownloadRequest,
) (user UserRecord, err error) {
	url := downloader.chessDotComUrl + "/pub/player/" + downloadRequest.User
	request, err := http.NewRequest("GET", url, bytes.NewBuffer([]byte{}))

	if err != nil {
		logger.Error("impossible to create a request to chess.com!", zap.String("url", url))
		return
	}

	logger.Info("requesting chess.com for profile", zap.String("url", url))
	response, err := chessDotComClient.Do(request)
	if err != nil {
		return
	}
	if response.StatusCode == 404 {
		logger.Error(fmt.Sprintf("profile %v not found on chess.com!", downloadRequest.User), zap.String("url", url))
		err = ProfileNotFound(downloadRequest)
		return
	}

	if response.StatusCode != 200 {
		logger.Error("unexpected status code from chess.com", zap.String("url", url), zap.Int("statusCode", response.StatusCode))
		err = ServiceOverloaded
		return
	}

	defer response.Body.Close()

	responseBodyBytes, err := io.ReadAll(response.Body)
	if err != nil {
		logger.Error("impossible to read the response body from chess.com!", zap.Error(err), zap.String("url", url))
		return
	}

	responseBodyString := string(responseBodyBytes)
	profile := ChessDotComProfile{}
	err = json.Unmarshal([]byte(responseBodyString), &profile)
	if err != nil {
		logger.Error("impossible to unmarshal the response body from chess.com!", zap.Error(err), zap.String("responseBody", responseBodyString), zap.String("url", url))
		return
	}

	logger.Info("profile found", zap.String("userId", profile.UserId), zap.String("userName", downloadRequest.User))

	user = UserRecord{
		Username: downloadRequest.User,
		UserId:   profile.UserId,
	}

	_, err = dynamodbClient.PutItem(user.toPutItem(downloader.usersTableName))
	if err != nil {
		logger.Error("impossible to persist the user!", zap.Error(err))
		return
	}
	logger.Info("user persisted", zap.String("userId", user.UserId), zap.String("userName", user.Username))

	return
}

func (downloader ArchiveDownloader) getArchivesFromChessDotCom(
	logger *zap.Logger,
	user UserRecord,
) (archives ChessDotComArchives, err error) {
	url := downloader.chessDotComUrl + "/pub/player/" + user.Username + "/games/archives"

	logger.Info("requesting chess.com for archives", zap.String("url", url))
	request, err := http.NewRequest("GET", url, bytes.NewBuffer([]byte{}))
	if err != nil {
		logger.Error("impossible to create a request to chess.com!", zap.String("url", url))
		return
	}

	response, err := http.DefaultClient.Do(request)
	if err != nil {
		logger.Error("impossible to request chess.com!", zap.String("url", url))
		return
	}
	if response.StatusCode != 200 {
		logger.Error("unexpected status code from chess.com", zap.String("url", url), zap.Int("statusCode", response.StatusCode))
		err = ServiceOverloaded
		return
	}

	defer response.Body.Close()

	responseBodyBytes, err := io.ReadAll(response.Body)
	if err != nil {
		logger.Error("impossible to read the response body from chess.com!", zap.Error(err), zap.String("url", url))
		return
	}

	responseBodyString := string(responseBodyBytes)
	archives = ChessDotComArchives{}
	err = json.Unmarshal([]byte(responseBodyString), &archives)
	if err != nil {
		logger.Error("impossible to unmarshal the response body from chess.com!", zap.Error(err), zap.String("responseBody", responseBodyString), zap.String("url", url))
		return
	}

	return
}

func (downloader ArchiveDownloader) getArchivesFromDb(
	dynamodbClient *dynamodb.DynamoDB,
	logger *zap.Logger,
	user UserRecord,
) (archives []ArchiveRecord, err error) {
	logger.Info("requesting dynamodb for archives", zap.String("userId", user.UserId))
	response, err := dynamodbClient.Query(&dynamodb.QueryInput{
		TableName: aws.String(downloader.archivesTableName),
		KeyConditions: map[string]*dynamodb.Condition{
			"user_id": {
				ComparisonOperator: aws.String("EQ"),
				AttributeValueList: []*dynamodb.AttributeValue{
					{
						S: aws.String(user.UserId),
					},
				},
			},
		},
	})
	if err != nil {
		logger.Error("impossible to query dynamodb for archives!", zap.Error(err))
		return
	}

	archives = make([]ArchiveRecord, len(response.Items))
	for i, item := range response.Items {
		archive := ArchiveRecord{}
		err = dynamodbattribute.UnmarshalMap(item, &archive)
		if err != nil {
			logger.Error("impossible to unmarshal the archive!", zap.Error(err))
			return
		}
		archives[i] = archive
	}
	logger.Info("archives found", zap.Int("archivesCount", len(archives)), zap.String("userId", user.UserId))

	return
}

func resolveMissingArchives(
	archivesFromChessDotCom ChessDotComArchives,
	archivesFromDb []ArchiveRecord,
) (missingArchives []string) {
	existingArchives := make(map[string]ArchiveRecord, len(archivesFromDb))
	for _, archiveFromDb := range archivesFromDb {
		existingArchives[archiveFromDb.ArchiveId] = archiveFromDb
	}

	missingArchives = make([]string, 0)
	for _, archiveFromChessDotCom := range archivesFromChessDotCom.Archives {
		if _, ok := existingArchives[archiveFromChessDotCom]; !ok {
			missingArchives = append(missingArchives, archiveFromChessDotCom)
		}
	}
	return
}

func resolveArchivesToDownload(
	archivesFromDb []ArchiveRecord,
) (archivesToDownload []ArchiveRecord) {
	archivesToDownload = make([]ArchiveRecord, 0)
	for _, archiveFromDb := range archivesFromDb {
		if archiveFromDb.Status == NotDownloaded || archiveFromDb.Status == PartiallyDownloaded {
			archivesToDownload = append(archivesToDownload, archiveFromDb)
		}
	}
	return
}

func (downloader ArchiveDownloader) persistMissingArchives(
	dynamodbClient *dynamodb.DynamoDB,
	logger *zap.Logger,
	user UserRecord,
	missingArchiveUrls []string,
) (missingArchives []ArchiveRecord, err error) {
	logger.Info("persisting missing archives", zap.Int("archivesCount", len(missingArchiveUrls)), zap.String("userId", user.UserId))
	for _, missingArchiveUrl := range missingArchiveUrls {

		archiveSegments := strings.Split(missingArchiveUrl, "/")
		maybeYear := archiveSegments[len(archiveSegments)-2]
		maybeMonth := archiveSegments[len(archiveSegments)-1]

		var year int
		year, err = strconv.Atoi(maybeYear)
		if err != nil {
			logger.Error("impossible to parse the year!", zap.Error(err), zap.String("userId", user.UserId))
			return
		}

		var month int
		month, err = strconv.Atoi(maybeMonth)
		if err != nil {
			logger.Error("impossible to parse the month!", zap.Error(err), zap.String("userId", user.UserId))
			return
		}

		till := time.Date(year, time.Month(month)+1, 1, 0, 0, 0, 0, time.Local)

		missingArchive := ArchiveRecord{
			UserId:     user.UserId,
			ArchiveId:  missingArchiveUrl,
			Resource:   missingArchiveUrl,
			Till:       till,
			Downloaded: 0,
			Status:     NotDownloaded,
		}

		_, err = dynamodbClient.PutItem(missingArchive.toPutItem(downloader.archivesTableName))
		if err != nil {
			logger.Error("impossible to persist the archive!", zap.Error(err), zap.String("userId", user.UserId))
			return
		}

		missingArchives = append(missingArchives, missingArchive)
	}
	logger.Info("missing archives persisted", zap.Int("archivesCount", len(missingArchiveUrls)), zap.String("userId", user.UserId))
	return
}

func (downloader ArchiveDownloader) publishDownloadGameCommands(
	logger *zap.Logger,
	svc *sqs.SQS,
	user UserRecord,
	downloadStatus DownloadStatusRecord,
	missingArchives []ArchiveRecord,
	shouldBeDownloadedArchives []ArchiveRecord,
) (err error) {
	logger.Info("publishing download game commands", zap.Int("archivesCount", len(missingArchives)+len(shouldBeDownloadedArchives)))
	allArchives := append(shouldBeDownloadedArchives, missingArchives...)
	for _, archive := range allArchives {
		command := DownloadGameCommand{
			UserName:          user.Username,
			Platform:          "CHESS_DOT_COM",
			ArchiveId:         archive.ArchiveId,
			UserId:            archive.UserId,
			DownloadRequestId: downloadStatus.DownloadRequestId,
		}
		var jsonBody []byte
		jsonBody, err = json.Marshal(command)
		if err != nil {
			logger.Error("impossible to marshal the download game command!", zap.Error(err), zap.String("archiveId", archive.ArchiveId))
			return err
		}

		_, err = svc.SendMessage(&sqs.SendMessageInput{
			QueueUrl:               aws.String(downloader.downloadGamesQueueUrl),
			MessageBody:            aws.String(string(jsonBody)),
			MessageDeduplicationId: aws.String(archive.ArchiveId),
			MessageGroupId:         aws.String(archive.UserId),
		})
		if err != nil {
			logger.Error("impossible to publish the download game command!", zap.Error(err), zap.String("archiveId", archive.ArchiveId))
			return
		}
	}

	logger.Info("download game commands published", zap.Int("archivesCount", len(allArchives)))
	return
}
