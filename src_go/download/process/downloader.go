package main

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/batcher"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/archives"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/downloads"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/games"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

type GameDownloader struct {
	chessDotComUrl               string
	downloadsTableName           string
	archivesTableName            string
	gamesTableName               string
	gamesByEndTimestampIndexName string
	awsConfig                    *aws.Config
}

func (downloader *GameDownloader) Download(commands events.SQSEvent) (commandsProcessed events.SQSEventResponse, err error) {
	config := zap.NewProductionConfig()
	config.OutputPaths = []string{"stdout"}
	timeEncoder := func(t time.Time, enc zapcore.PrimitiveArrayEncoder) {
		enc.AppendString(t.UTC().Format("2006-01-02T15:04:05.000Z"))
	}
	config.EncoderConfig.EncodeTime = timeEncoder

	logger, err := config.Build()
	if err != nil {
		return
	}
	defer logger.Sync()
	awsSession, err := session.NewSession(downloader.awsConfig)
	if err != nil {
		logger.Error("impossible to create an AWS session!")
		return
	}
	dynamodbClient := dynamodb.New(awsSession)
	chessDotComClient := &http.Client{}

	logger.Info("Processing commands in total", zap.Int("commands", len(commands.Records)))

	for _, message := range commands.Records {
		_, _ = downloader.processSingle(&message, dynamodbClient, chessDotComClient, logger)
	}
	return
}

func (downloader *GameDownloader) processSingle(
	message *events.SQSMessage,
	dynamodbClient *dynamodb.DynamoDB,
	chessDotComClient *http.Client,
	logger *zap.Logger,
) (commandProcessed *events.SQSBatchItemFailure, err error) {
	command := queue.DownloadGamesCommand{}
	err = json.Unmarshal([]byte(message.Body), &command)
	if err != nil {
		logger.Error("impossible to unmarshal the command", zap.Error(err))
		return
	}

	logger = logger.With(zap.String("userId", command.UserId), zap.String("archiveId", command.ArchiveId), zap.String("downloadId", command.DownloadId))
	logger.Info("Processing command")

	incrementDownloadStatus := func(incrementSuccess bool) (err error) {

		logger.Info("incrementing the download status")
		downloadRecord := downloads.DownloadRecord{}
		downloadRecordItems, err := dynamodbClient.GetItem(&dynamodb.GetItemInput{
			TableName: aws.String(downloader.downloadsTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"download_id": {
					S: aws.String(command.DownloadId),
				},
			},
		})

		if err != nil {
			logger.Error("impossible to get the download record", zap.Error(err))
			return
		}

		if downloadRecordItems.Item == nil || len(downloadRecordItems.Item) == 0 {
			logger.Error("download record not found")
			return
		}

		err = dynamodbattribute.UnmarshalMap(downloadRecordItems.Item, &downloadRecord)
		if err != nil {
			logger.Error("impossible to unmarshal the download record", zap.Error(err))
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

		updatedDownloadRecordItems, err := dynamodbattribute.MarshalMap(downloadRecord)

		if err != nil {
			logger.Error("impossible to marshal the download record", zap.Error(err))
			return
		}

		_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
			TableName: aws.String(downloader.downloadsTableName),
			Item:      updatedDownloadRecordItems,
		})

		if err != nil {
			logger.Error("impossible to update the download record", zap.Error(err))
			return
		}
		return
	}

	unsafeProcessSingle := func() (err error) {
		archiveRecord := archives.ArchiveRecord{}
		archiveRecordItems, err := dynamodbClient.GetItem(&dynamodb.GetItemInput{
			TableName: aws.String(downloader.archivesTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"user_id": {
					S: aws.String(command.UserId),
				},
				"archive_id": {
					S: aws.String(command.ArchiveId),
				},
			},
		})

		if err != nil {
			logger.Error("impossible to get the archive record", zap.Error(err))
			return
		}

		if archiveRecordItems.Item == nil || len(archiveRecordItems.Item) == 0 {
			logger.Error("archive record not found")
			errOfIncrement := incrementDownloadStatus(true)
			if errOfIncrement != nil {
				logger.Error("impossible to increment the download status", zap.Error(err))
			}
			return
		}

		err = dynamodbattribute.UnmarshalMap(archiveRecordItems.Item, &archiveRecord)
		if err != nil {
			logger.Error("impossible to unmarshal the archive record", zap.Error(err))
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

		latestDownloadedGameRecord := games.GameRecord{}
		latestDownloadedGameRecordItems, err := dynamodbClient.Query(&dynamodb.QueryInput{
			TableName:              aws.String(downloader.gamesTableName),
			IndexName:              aws.String(downloader.gamesByEndTimestampIndexName),
			KeyConditionExpression: aws.String("archive_id = :archive_id"),
			ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
				":archive_id": {
					S: aws.String(command.ArchiveId),
				},
			},
			ScanIndexForward: aws.Bool(false),
			Limit:            aws.Int64(1),
		})

		if err != nil {
			logger.Error("impossible to get the latest downloaded game", zap.Error(err))
			return
		}

		if len(latestDownloadedGameRecordItems.Items) > 0 {
			err = dynamodbattribute.UnmarshalMap(latestDownloadedGameRecordItems.Items[0], &latestDownloadedGameRecord)
			if err != nil {
				logger.Error("impossible to unmarshal the latest downloaded game", zap.Error(err))
				return
			}
		}

		missingGameRecords := []games.GameRecord{}
		for _, chessDotComGame := range chessDotComGames.Games {
			if chessDotComGame.EndTime > latestDownloadedGameRecord.EndTimestamp {
				pgnString := string(chessDotComGame.Pgn)
				if strings.HasPrefix(pgnString, "\"") && strings.HasSuffix(pgnString, "\"") {
					pgnString = pgnString[1 : len(pgnString)-1]
				}
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

		missingGameRecordWriteRequests := []*dynamodb.WriteRequest{}
		for _, missingGameRecord := range missingGameRecords {
			var missingGameRecordItems map[string]*dynamodb.AttributeValue
			missingGameRecordItems, err = dynamodbattribute.MarshalMap(missingGameRecord)
			if err != nil {
				logger.Error("impossible to marshal the missing game record", zap.Error(err))
				return
			}
			writeRequest := &dynamodb.WriteRequest{
				PutRequest: &dynamodb.PutRequest{
					Item: missingGameRecordItems,
				},
			}
			missingGameRecordWriteRequests = append(missingGameRecordWriteRequests, writeRequest)
		}

		missingGameRecordWriteRequestsMatrix := batcher.Batcher(missingGameRecordWriteRequests, 25)

		logger.Info("trying to persist missing games in batches", zap.Int("batches", len(missingGameRecordWriteRequestsMatrix)))

		for bacthNumber, batch := range missingGameRecordWriteRequestsMatrix {
			logger := logger.With(zap.Int("batchNumber", bacthNumber+1), zap.Int("batchSize", len(batch)))
			logger.Info("trying to persist a batch of missing games")

			unprocessedWriteRequests := map[string][]*dynamodb.WriteRequest{
				downloader.gamesTableName: batch,
			}

			for len(unprocessedWriteRequests) > 0 {
				logger.Info("trying to persist missing games in one iteration")
				var writeOutput *dynamodb.BatchWriteItemOutput
				writeOutput, err = dynamodbClient.BatchWriteItem(&dynamodb.BatchWriteItemInput{
					RequestItems: unprocessedWriteRequests,
				})

				if err != nil {
					logger.Error("impossible to persist the missing game records", zap.Error(err))
					return
				}

				unprocessedWriteRequests = writeOutput.UnprocessedItems
				time.Sleep(time.Millisecond * 100)
			}
		}

		nowInZulu := db.Zuludatetime(now)
		archiveRecord.DownloadedAt = &nowInZulu
		archiveRecord.Downloaded += int(len(missingGameRecords))

		logger.Info("updating the archive record")

		updatedArchiveRecordItems, err := dynamodbattribute.MarshalMap(archiveRecord)

		if err != nil {
			logger.Error("impossible to marshal the archive record", zap.Error(err))
			return
		}

		_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
			TableName: aws.String(downloader.archivesTableName),
			Item:      updatedArchiveRecordItems,
		})

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
