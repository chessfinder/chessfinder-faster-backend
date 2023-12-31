package main

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/cloudwatch"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/archives"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/downloads"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/games"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/metrics"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue"
	"go.uber.org/zap"
)

type GameDownloader struct {
	chessDotComUrl               string
	downloadsTableName           string
	archivesTableName            string
	gamesTableName               string
	gamesByEndTimestampIndexName string
	metricsNamespace             string
	awsConfig                    *aws.Config
}

func (downloader *GameDownloader) Download(commands events.SQSEvent) (events.SQSEventResponse, error) {
	logger := logging.MustCreateZuluTimeLogger()
	defer logger.Sync()
	failedEvents := queue.ProcessMultiple(commands, downloader, logger)
	return failedEvents, nil
}

func (downloader *GameDownloader) ProcessSingle(
	message *events.SQSMessage,
	logger *zap.Logger,
) (commandProcessed *events.SQSBatchItemFailure, err error) {

	awsSession, err := session.NewSession(downloader.awsConfig)
	if err != nil {
		logger.Error("impossible to create an AWS session!")
		return
	}
	dynamodbClient := dynamodb.New(awsSession)
	chessDotComClient := &http.Client{}
	cloudWatchClient := cloudwatch.New(awsSession)

	command := queue.DownloadGamesCommand{}
	err = json.Unmarshal([]byte(message.Body), &command)
	if err != nil {
		logger.Error("impossible to unmarshal the command", zap.Error(err))
		return
	}

	logger = logger.With(zap.String("userId", command.UserId))
	logger = logger.With(zap.String("archiveId", command.ArchiveId))
	logger = logger.With(zap.String("downloadId", command.DownloadId))
	logger = logger.With(zap.String("username", command.Username))
	logger = logger.With(zap.String("platform", string(command.Platform)))

	logger.Info("Processing command")

	downloadsTable := downloads.DownloadsTable{
		Name:           downloader.downloadsTableName,
		DynamodbClient: dynamodbClient,
	}

	incrementDownloadStatus := func(incrementSuccess bool) (err error) {

		logger.Info("incrementing the download status")
		downloadRecord, err := downloadsTable.GetDownloadRecord(command.DownloadId)

		if err != nil {
			logger.Error("impossible to get the download record", zap.Error(err))
			return
		}

		if downloadRecord == nil {
			logger.Error("download record not found")
			return
		}

		if downloadRecord.Total <= downloadRecord.Done {
			logger.Error("inconsitent download record", zap.Int("total", downloadRecord.Total), zap.Int("done", downloadRecord.Done))
			return
		}

		downloadRecord.Pending--
		if incrementSuccess {
			downloadRecord.Succeed++
		} else {
			downloadRecord.Failed++
		}
		downloadRecord.Done++

		err = downloadsTable.PutDownloadRecord(*downloadRecord)

		if err != nil {
			logger.Error("impossible to marshal the download record", zap.Error(err))
			return
		}

		return
	}

	archivesTable := archives.ArchivesTable{
		Name:           downloader.archivesTableName,
		DynamodbClient: dynamodbClient,
	}

	unsafeProcessSingle := func() (err error) {
		archiveRecord, err := archivesTable.GetArchiveRecord(command.UserId, command.ArchiveId)

		if err != nil {
			logger.Error("impossible to get the archive record", zap.Error(err))
			return
		}

		if archiveRecord == nil {
			logger.Error("archive record not found")
			errOfIncrement := incrementDownloadStatus(true)
			if errOfIncrement != nil {
				logger.Error("impossible to increment the download status", zap.Error(err))
			}
			return
		}

		archiveHasGamesTill := time.Date(archiveRecord.Year, time.Month(archiveRecord.Month), 1, 0, 0, 0, 0, time.UTC).AddDate(0, 1, 0)
		if archiveRecord.DownloadedAt != nil && !archiveRecord.DownloadedAt.ToTime().Before(archiveHasGamesTill) {
			logger.Info("archive already downloaded")
			errOfIncrement := incrementDownloadStatus(true)
			if errOfIncrement != nil {
				logger.Error("impossible to increment the download status", zap.Error(err))
			}
			return
		}

		now := time.Now()
		chessDotComGames := ChessDotComGames{}
		// make this validation while reading envarionment variables
		requestUrl, err := url.ParseRequestURI(downloader.chessDotComUrl)
		if err != nil {
			logger.Error("impossible to parse the chess.com url", zap.Error(err))
			return
		}
		monthInString := strconv.Itoa(archiveRecord.Month)
		if len(monthInString) == 1 {
			monthInString = "0" + monthInString
		}
		requestUrl.Path = "/pub/player/" + command.Username + "/games/" + strconv.Itoa(archiveRecord.Year) + "/" + monthInString
		url := requestUrl.String()
		logger.Info("requesting games", zap.String("url", url))

		downloadGamesRequest, err := http.NewRequest("GET", url, nil)
		if err != nil {
			logger.Error("Error while creating request to download games", zap.Error(err))
			return
		}
		downloadGamesRequest.Header["Accept"] = []string{"application/json"}
		response, err := chessDotComClient.Do(downloadGamesRequest)
		if err != nil {
			logger.Error("impossible to get the games", zap.Error(err))
			return
		}
		defer response.Body.Close()

		chessDotComMeter := metrics.ChessDotComMeter{
			Namespace:        downloader.metricsNamespace,
			CloudWatchClient: cloudWatchClient,
		}

		errFromMetricRegistration := chessDotComMeter.ChessDotComStatistics(metrics.GetGames, response.StatusCode)
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
			err = errors.New("unexpected status code from chess.com")
			return
		}

		err = json.Unmarshal(responseBodyBytes, &chessDotComGames)
		if err != nil {
			logger.Error("impossible to unmarshal the games", zap.Error(err))
			return
		}

		logger = logger.With(zap.Int("allDownloadedGames", len(chessDotComGames.Games)))

		if len(chessDotComGames.Games) == 0 {
			logger.Info("no games found")
			errOfIncrement := incrementDownloadStatus(true)
			if errOfIncrement != nil {
				logger.Error("impossible to increment the download status", zap.Error(err))
			}
			return
		}

		latestDownloadedGameRecord, err :=
			games.LatestGameIndex{
				Name:           downloader.gamesByEndTimestampIndexName,
				TableName:      downloader.gamesTableName,
				DynamodbClient: dynamodbClient,
			}.QueryByEndTimestamp(command.ArchiveId)

		if err != nil {
			logger.Error("impossible to get the latest downloaded game", zap.Error(err))
			return
		}

		missingGameRecords := []games.GameRecord{}
		for _, chessDotComGame := range chessDotComGames.Games {
			isGameMissing := latestDownloadedGameRecord == nil || (latestDownloadedGameRecord != nil && chessDotComGame.EndTime > latestDownloadedGameRecord.EndTimestamp)
			if isGameMissing {
				pgnString := string(chessDotComGame.Pgn)
				gameRecord := games.GameRecord{
					UserId:       command.UserId,
					ArchiveId:    command.ArchiveId,
					GameId:       chessDotComGame.Url,
					Resource:     chessDotComGame.Url,
					Pgn:          pgnString,
					EndTimestamp: chessDotComGame.EndTime,
				}
				missingGameRecords = append(missingGameRecords, gameRecord)
			}
		}

		logger = logger.With(zap.Int("missingGames", len(missingGameRecords)))
		logger.Info("persisiting missing games")

		err = games.GamesTable{
			Name:           downloader.gamesTableName,
			DynamodbClient: dynamodbClient,
		}.PutGameRecords(missingGameRecords)

		if err != nil {
			logger.Error("impossible to persist the missing game records", zap.Error(err))
			return
		}

		nowInZulu := db.Zuludatetime(now)
		archiveRecord.DownloadedAt = &nowInZulu
		archiveRecord.Downloaded += int(len(missingGameRecords))

		logger.Info("updating the archive record")

		err = archivesTable.PutArchiveRecord(*archiveRecord)

		if err != nil {
			logger.Error("impossible to update the archive record", zap.Error(err))
			return
		}

		errOfIncrement := incrementDownloadStatus(true)
		if errOfIncrement != nil {
			logger.Error("impossible to increment the download status", zap.Error(err))
		}

		return
	}

	err = unsafeProcessSingle()
	if err != nil {
		logger.Error("impossible to process the command", zap.Error(err))
		errOfIncrement := incrementDownloadStatus(false)
		if errOfIncrement != nil {
			logger.Error("impossible to increment the download status", zap.Error(err))
		}
		return
	}

	return
}
