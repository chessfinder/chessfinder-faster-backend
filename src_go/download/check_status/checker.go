package main

import (
	"encoding/json"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/downloads"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

type DownloadStatusChecker struct {
	awsConfig          *aws.Config
	downloadsTableName string
}

func (checker *DownloadStatusChecker) Check(event *events.APIGatewayV2HTTPRequest) (responseEvent events.APIGatewayV2HTTPResponse, err error) {

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

	awsSession, err := session.NewSession(checker.awsConfig)
	if err != nil {
		logger.Panic("impossible to create an AWS session!", zap.Error(err))
	}

	dynamodbClient := dynamodb.New(awsSession)

	method := event.RequestContext.HTTP.Method
	path := event.RequestContext.HTTP.Path

	if path != "/api/faster/game" || method != "GET" {
		logger.Panic("downloading status checker is attached to a wrong route!")
	}

	downloadId, downloadIdExists := event.QueryStringParameters["downloadId"]
	if !downloadIdExists {
		err = api.ValidationError{
			Msg: "query parameter downloadId is missing",
		}
		return
	}

	logger = logger.With(zap.String("downloadId", downloadId))

	downloadRecordCandidate, err := downloads.DownloadsTable{
		Name:           checker.downloadsTableName,
		DynamodbClient: dynamodbClient,
	}.GetDownloadRecord(downloadId)

	if err != nil {
		logger.Error("faild to get download record!", zap.Error(err))
		return
	}

	if downloadRecordCandidate == nil {
		logger.Error("no download record found!", zap.String("downloadId", downloadId))
		err = DownloadNotFound(downloadId)
		return
	}

	downloadRecord := *downloadRecordCandidate

	downloadStatusResponse := DownloadStatusResponse{
		DownloadId: downloadRecord.DownloadId,
		Failed:     downloadRecord.Failed,
		Succeed:    downloadRecord.Succeed,
		Done:       downloadRecord.Done,
		Pending:    downloadRecord.Pending,
		Total:      downloadRecord.Total,
	}

	responseBody, err := json.Marshal(downloadStatusResponse)
	if err != nil {
		logger.Error("faild to marshal download response", zap.Error(err))
		return
	}
	responseEvent = events.APIGatewayV2HTTPResponse{
		Body:       string(responseBody),
		StatusCode: 200,
		Headers: map[string]string{
			"Content-Type": "application/json",
		},
	}
	return
}
