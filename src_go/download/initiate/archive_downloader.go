package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/cloudwatch"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/sqs"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/archives"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/downloads"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/users"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/metrics"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue"
	"github.com/google/uuid"
	"go.uber.org/zap"
)

const DownloadInfoUpToNSecondsBeforeExpires = time.Second * 10

type ArchiveDownloader struct {
	chessDotComUrl                           string
	awsConfig                                *aws.Config
	usersTableName                           string
	archivesTableName                        string
	downloadsTableName                       string
	downloadsByConsistentDownloadIdIndexName string
	downloadGamesQueueUrl                    string
	metricsNamespace                         string
	downloadInfoExpiresIn                    time.Duration
}

func (downloader *ArchiveDownloader) DownloadArchiveAndDistributeDownloadGameCommands(
	event *events.APIGatewayV2HTTPRequest,
) (responseEvent events.APIGatewayV2HTTPResponse, err error) {
	logger := logging.MustCreateZuluTimeLogger()
	logger = logger.With(zap.String("requestId", event.RequestContext.RequestID))
	defer logger.Sync()

	awsSession, err := session.NewSession(downloader.awsConfig)
	if err != nil {
		logger.Panic("impossible to create an AWS session!")
		return
	}
	dynamodbClient := dynamodb.New(awsSession)
	sqsClient := sqs.New(awsSession)
	cloudWatchClient := cloudwatch.New(awsSession)
	chessDotComClient := &http.Client{}

	method := event.RequestContext.HTTP.Method
	path := event.RequestContext.HTTP.Path

	if path != "/api/faster/game" || method != "POST" {
		logger.Panic("archive downloader is attached to a wrong route!")
	}
	downloadRequest := DownloadRequest{}
	err = json.Unmarshal([]byte(event.Body), &downloadRequest)
	if err != nil {
		logger.Error("impossible to unmarshal the request body!")
		err = api.InvalidBody
		return
	}

	downloadRequest.Username = strings.ToLower(downloadRequest.Username)

	logger = logger.With(zap.String("username", downloadRequest.Username), zap.String("platform", downloadRequest.Platform))

	usersTable := users.UsersTable{
		Name:           downloader.usersTableName,
		DynamodbClient: dynamodbClient,
	}

	logger.Info("checking for existing user record...")

	var profile users.UserRecord

	profileCandidate, err := usersTable.GetUserRecord(downloadRequest.Username, users.ChessDotCom)

	if err != nil {
		logger.Error("impossible to get the user from the database", zap.Error(err))
		return
	}

	chessDotComMeter := metrics.ChessDotComMeter{
		Namespace:        downloader.metricsNamespace,
		CloudWatchClient: cloudWatchClient,
	}

	if profileCandidate == nil {
		logger.Info("user not found in the database. Downloading from chess.com ...")
		profileCandidate, err = downloader.getAndPersistUser(dynamodbClient, chessDotComClient, chessDotComMeter, logger, downloadRequest)
		if err != nil {
			return
		}
		if profileCandidate == nil {
			logger.Error("impossible to get the user from chess.com!")
			err = api.ServiceOverloaded
			return
		}
	}

	profile = *profileCandidate

	logger = logger.With(zap.String("userId", profile.UserId))

	logger.Info("checking for existing download record...")

	existingDownloadRecord, err := downloads.DownloadsByConsistentDownloadIdIndex{
		Name:           downloader.downloadsByConsistentDownloadIdIndexName,
		TableName:      downloader.downloadsTableName,
		DynamodbClient: dynamodbClient,
	}.LatestDownload(downloads.NewConsistentDownloadId(profile.UserId))

	if err != nil {
		logger.Error("impossible to get the latest download record!", zap.Error(err))
		return
	}

	now := time.Now()
	if existingDownloadRecord != nil {
		downloadInfoCanBeUsedUpTo := time.Time(existingDownloadRecord.ExpiresAt).Add(-DownloadInfoUpToNSecondsBeforeExpires)
		logger.Info("download record found", zap.String("downloadId", existingDownloadRecord.DownloadId))
		if now.Before(downloadInfoCanBeUsedUpTo) {
			logger.Info("download record is still valid. Returning the existing download id")
			responseEvent, err = downloadIdToResponseEvent(existingDownloadRecord.DownloadId)
			if err != nil {
				logger.Error("impossible to create the response event!", zap.Error(err))
			}

			return
		}

		logger.Info("download record is almost expired. Downloading again...", zap.Time("expiresAt", time.Time(existingDownloadRecord.ExpiresAt)))
	}

	logger.Info("no download record found. Downloading...")

	archivesFromChessDotCom, err := downloader.getArchivesFromChessDotCom(chessDotComClient, chessDotComMeter, logger, profile)
	if err != nil {
		return
	}

	logger.Info("requesting dynamodb for archives")
	var partaillyDownloadedArchives []archives.ArchiveRecord
	var missingArchiveUrls []string

	if profile.DownloadFromScratch {
		logger.Info("downloading from scratch is enabled. all archives will be downloaded. archives from database will be ignored")

		missingArchiveUrls = archivesFromChessDotCom.Archives
		partaillyDownloadedArchives = []archives.ArchiveRecord{}
	} else {
		var archivesFromDb []archives.ArchiveRecord

		archivesFromDb, err = archives.ArchivesTable{
			Name:           downloader.archivesTableName,
			DynamodbClient: dynamodbClient,
		}.GetArchiveRecords(profile.UserId)

		if err != nil {
			logger.Error("impossible to get archives!", zap.Error(err))
			return
		}

		logger.Info("archives found from database", zap.Int("totalArchivesCount", len(archivesFromDb)))

		missingArchiveUrls = resolveMissingArchives(archivesFromChessDotCom, archivesFromDb)
		partaillyDownloadedArchives = resolvePartiallyDownloadedArchives(archivesFromDb)
	}

	missingArchives, err := downloader.persistMissingArchives(dynamodbClient, logger, profile, missingArchiveUrls)
	if err != nil {
		return
	}

	downloadId := uuid.New().String()
	consistentDownloadId := downloads.NewConsistentDownloadId(profile.UserId)
	total := len(missingArchives) + len(partaillyDownloadedArchives)
	downloadRecord := downloads.NewDownloadRecord(downloadId, consistentDownloadId, total, now, downloader.downloadInfoExpiresIn)

	err = downloads.DownloadsTable{
		Name:           downloader.downloadsTableName,
		DynamodbClient: dynamodbClient,
	}.PutDownloadRecord(downloadRecord)

	if err != nil {
		logger.Error("impossible to persist the download record!", zap.Error(err))
		return
	}

	err = downloader.publishDownloadGameCommands(logger, sqsClient, profile, downloadRecord, missingArchives, partaillyDownloadedArchives)
	if err != nil {
		return
	}

	err = users.UsersTable{
		Name:           downloader.usersTableName,
		DynamodbClient: dynamodbClient,
	}.DownloadInitiated(profile.Username, profile.Platform)

	if err != nil {
		logger.Error("impossible to update the user record!", zap.Error(err))
		return
	}

	responseEvent, err = downloadIdToResponseEvent(downloadId)
	if err != nil {
		logger.Error("impossible to create the response event!", zap.Error(err))
	}

	return
}

func (downloader ArchiveDownloader) getAndPersistUser(
	dynamodbClient *dynamodb.DynamoDB,
	chessDotComClient *http.Client,
	chessDotComMeter metrics.ChessDotComMeter,
	logger *zap.Logger,
	downloadRequest DownloadRequest,
) (userRecord *users.UserRecord, err error) {
	url := downloader.chessDotComUrl + "/pub/player/" + downloadRequest.Username
	logger = logger.With(zap.String("url", url))

	request, err := http.NewRequest("GET", url, bytes.NewBuffer([]byte{}))

	if err != nil {
		logger.Error("impossible to create a request to chess.com!")
		return
	}

	response, err := chessDotComClient.Do(request)
	if err != nil {
		return
	}

	defer response.Body.Close()

	errFromMetricRegistration := chessDotComMeter.ChessDotComStatistics(metrics.GetProfile, response.StatusCode)
	if errFromMetricRegistration != nil {
		logger.Warn("impossible to register the metric ChessDotComStatistics", zap.Error(errFromMetricRegistration))
	}

	responseBodyBytes, err := io.ReadAll(response.Body)
	if err != nil {
		logger.Error("impossible to read the response body from chess.com!", zap.Error(err))
		return
	}

	responseBodyString := string(responseBodyBytes)

	if response.StatusCode == 404 {
		logger.Error("profile not found on chess.com!", zap.String("responseBody", responseBodyString))
		err = ProfileNotFound(downloadRequest)
		return
	}

	if response.StatusCode != 200 {
		logger.Error("unexpected status code from chess.com!", zap.Int("statusCode", response.StatusCode), zap.String("responseBody", responseBodyString))
		err = api.ServiceOverloaded
		return
	}

	profile := ChessDotComProfile{}
	err = json.Unmarshal([]byte(responseBodyString), &profile)
	if err != nil {
		logger.Error("impossible to unmarshal the response body from chess.com!", zap.Error(err), zap.String("responseBody", responseBodyString), zap.String("url", url))
		return
	}

	logger.Info("profile found in chess.com")

	// why we get it from the table after making a request to chess.com?
	usersTable := users.UsersTable{
		Name:           downloader.usersTableName,
		DynamodbClient: dynamodbClient,
	}

	logger.Info("user not found in the database")

	userRecordCandidate := users.UserRecord{
		Username:            downloadRequest.Username,
		UserId:              profile.UserId,
		Platform:            users.ChessDotCom,
		DownloadFromScratch: true,
	}

	logger.Info("persisting the user record in the database")

	err = usersTable.PutUserRecord(userRecordCandidate)
	if err != nil {
		logger.Error("impossible to persist the user record in the database", zap.Error(err))
		return
	}

	userRecord = &userRecordCandidate

	return
}

func (downloader ArchiveDownloader) getArchivesFromChessDotCom(
	chessDotComClient *http.Client,
	chessDotComMeter metrics.ChessDotComMeter,
	logger *zap.Logger,
	user users.UserRecord,
) (archives ChessDotComArchives, err error) {
	url := downloader.chessDotComUrl + "/pub/player/" + user.Username + "/games/archives"
	logger = logger.With(zap.String("url", url))
	logger.Info("requesting chess.com for archives")
	request, err := http.NewRequest("GET", url, bytes.NewBuffer([]byte{}))
	if err != nil {
		logger.Error("impossible to create a request to chess.com!")
		return
	}

	response, err := chessDotComClient.Do(request)
	if err != nil {
		logger.Error("impossible to request chess.com!")
		return
	}

	defer response.Body.Close()

	errFromMetricRegistration := chessDotComMeter.ChessDotComStatistics(metrics.GetArchives, response.StatusCode)
	if errFromMetricRegistration != nil {
		logger.Error("impossible to register the metric ChessDotComStatistics", zap.Error(errFromMetricRegistration))
	}

	responseBodyBytes, err := io.ReadAll(response.Body)
	if err != nil {
		logger.Error("impossible to read the response body from chess.com!", zap.Error(err))
		return
	}

	responseBodyString := string(responseBodyBytes)

	if response.StatusCode != 200 {
		logger.Error("unexpected status code from chess.com", zap.Int("statusCode", response.StatusCode), zap.String("responseBody", responseBodyString))
		err = api.ServiceOverloaded
		return
	}

	archives = ChessDotComArchives{}
	err = json.Unmarshal([]byte(responseBodyString), &archives)
	if err != nil {
		logger.Error("impossible to unmarshal the response body from chess.com!", zap.Error(err), zap.String("responseBody", responseBodyString))
		return
	}

	logger.Info("archives found from chess.com", zap.Int("existingArchivesCount", len(archives.Archives)))

	return
}

func resolveMissingArchives(
	archivesFromChessDotCom ChessDotComArchives,
	archivesFromDb []archives.ArchiveRecord,
) (missingArchives []string) {
	existingArchives := make(map[string]archives.ArchiveRecord, len(archivesFromDb))
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

func resolvePartiallyDownloadedArchives(
	archivesFromDb []archives.ArchiveRecord,
) (archivesToDownload []archives.ArchiveRecord) {
	archivesToDownload = make([]archives.ArchiveRecord, 0)
	for _, archiveFromDb := range archivesFromDb {
		if archiveFromDb.DownloadedAt == nil {
			archivesToDownload = append(archivesToDownload, archiveFromDb)
			continue
		}
		archiveHasGamesTill := time.Date(archiveFromDb.Year, time.Month(archiveFromDb.Month), 1, 0, 0, 0, 0, time.UTC).AddDate(0, 1, 0)
		if archiveFromDb.DownloadedAt.ToTime().Before(archiveHasGamesTill) {
			archivesToDownload = append(archivesToDownload, archiveFromDb)
		}
	}
	return
}

func (downloader ArchiveDownloader) persistMissingArchives(
	dynamodbClient *dynamodb.DynamoDB,
	logger *zap.Logger,
	user users.UserRecord,
	missingArchiveUrls []string,
) (missingArchiveRecords []archives.ArchiveRecord, err error) {
	logger.Info("persisting missing archives", zap.Int("missingArchivesCount", len(missingArchiveUrls)))

	for _, missingArchiveUrl := range missingArchiveUrls {
		logger := logger.With(zap.String("archiveId", missingArchiveUrl))
		archiveSegments := strings.Split(missingArchiveUrl, "/")
		maybeYear := archiveSegments[len(archiveSegments)-2]
		maybeMonth := archiveSegments[len(archiveSegments)-1]

		var year int
		year, err = strconv.Atoi(maybeYear)
		if err != nil {
			logger.Error("impossible to parse the year!", zap.Error(err))
			return
		}

		var month int
		month, err = strconv.Atoi(maybeMonth)
		if err != nil {
			logger.Error("impossible to parse the month!", zap.Error(err))
			return
		}

		missingArchiveRecord := archives.ArchiveRecord{
			UserId:       user.UserId,
			ArchiveId:    missingArchiveUrl,
			Resource:     missingArchiveUrl,
			Year:         year,
			Month:        month,
			Downloaded:   0,
			DownloadedAt: nil,
		}

		missingArchiveRecords = append(missingArchiveRecords, missingArchiveRecord)
	}

	err = archives.ArchivesTable{
		Name:           downloader.archivesTableName,
		DynamodbClient: dynamodbClient,
	}.PutArchiveRecords(missingArchiveRecords)

	if err != nil {
		logger.Error("impossible to persist the missing archive records", zap.Error(err))
		return
	}

	logger.Info("missing archives persisted", zap.Int("persistedArchivesCount", len(missingArchiveUrls)))
	return
}

func (downloader ArchiveDownloader) publishDownloadGameCommands(
	logger *zap.Logger,
	svc *sqs.SQS,
	user users.UserRecord,
	downloadRecords downloads.DownloadRecord,
	missingArchives []archives.ArchiveRecord,
	shouldBeDownloadedArchives []archives.ArchiveRecord,
) (err error) {
	allArchives := append(shouldBeDownloadedArchives, missingArchives...)
	logger = logger.With(zap.Int("eligibleForDownloadArchivesCount", len(allArchives)))
	logger.Info("publishing download game commands ...")

	for _, archive := range allArchives {
		logger := logger.With(zap.String("archiveId", archive.ArchiveId))
		command := queue.DownloadGamesCommand{
			Username:   user.Username,
			Platform:   "CHESS_DOT_COM",
			ArchiveId:  archive.ArchiveId,
			UserId:     archive.UserId,
			DownloadId: downloadRecords.DownloadId,
		}
		var jsonBody []byte
		jsonBody, err = json.Marshal(command)
		if err != nil {
			logger.Error("impossible to marshal the download game command!", zap.Error(err))
			return err
		}

		_, err = svc.SendMessage(&sqs.SendMessageInput{
			QueueUrl:               aws.String(downloader.downloadGamesQueueUrl),
			MessageBody:            aws.String(string(jsonBody)),
			MessageDeduplicationId: aws.String(archive.ArchiveId),
			MessageGroupId:         aws.String(archive.UserId),
		})
		if err != nil {
			logger.Error("impossible to publish the download game command!", zap.Error(err))
			return
		}
	}

	logger.Info("download game commands published")
	return
}

func downloadIdToResponseEvent(downloadId string) (responseEvent events.APIGatewayV2HTTPResponse, err error) {
	downloadResponse := DownloadResponse{
		DownloadId: downloadId,
	}

	jsonBody, err := json.Marshal(downloadResponse)
	if err != nil {
		return
	}

	responseEvent = events.APIGatewayV2HTTPResponse{
		StatusCode: 200,
		Body:       string(jsonBody),
		Headers: map[string]string{
			"Content-Type": "application/json",
		},
	}

	return
}
