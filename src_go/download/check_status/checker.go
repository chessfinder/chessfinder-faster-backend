package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/api"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

type DownloadStatusChecker struct {
	awsConfig                *aws.Config
	downloadRequestTableName string
}

func (checker *DownloadStatusChecker) checkWithFastFail(event *events.APIGatewayV2HTTPRequest) (responseEvent events.APIGatewayV2HTTPResponse, err error) {

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
		logger.Error("impossible to create an AWS session!")
		return
	}
	dynamodbClient := dynamodb.New(awsSession)

	method := event.RequestContext.HTTP.Method
	path := event.RequestContext.HTTP.Path

	if path != "/api/faster/game" || method != "GET" {
		logger.Error("downloading status checker is attached to a wrong route!")
		// fixme check if error is 500. if not consider panicing
		err = errors.New("not supported")
		return
	}
	downloadRequestId, downloadRequestIdExists := event.QueryStringParameters["downloadRequestId"]
	if !downloadRequestIdExists {
		err = api.ValidationError{
			Msg: "query parameter downloadRequestId is missing",
		}
		return
	}

	input := &dynamodb.GetItemInput{
		Key: map[string]*dynamodb.AttributeValue{
			"task_id": {
				S: aws.String(downloadRequestId),
			},
		},
		TableName: aws.String(checker.downloadRequestTableName),
	}

	downloadRequestStatusAttributes, err := dynamodbClient.GetItem(input)
	if err != nil {
		logger.Error(fmt.Sprintf("faild to get record %v from the %v. Error %v", downloadRequestId, checker.downloadRequestTableName, err.Error()))
		return
	}

	if len(downloadRequestStatusAttributes.Item) == 0 {
		logger.Error(fmt.Sprintf("no dowload request found with id %v in the %v", downloadRequestId, checker.downloadRequestTableName))
		err = DownloadRequestNotFound(downloadRequestId)
		return
	}

	downloadRequestStatusRecord := new(DownloadStatusRecord)
	err = dynamodbattribute.UnmarshalMap(downloadRequestStatusAttributes.Item, downloadRequestStatusRecord)
	if err != nil {
		logger.Error(fmt.Sprintf("faild to get record %v from the %v with the error %v", downloadRequestId, checker.downloadRequestTableName, err.Error()))
		return
	}
	downloadRequestStatusResponse := (*DownloadStatusResponse)(downloadRequestStatusRecord)
	responseBody, err := json.Marshal(downloadRequestStatusResponse)
	if err != nil {
		logger.Error(fmt.Sprintf("faild to marshal record %v from the %v with the error %v", downloadRequestId, checker.downloadRequestTableName, err.Error()))
		return
	}
	responseEvent = events.APIGatewayV2HTTPResponse{
		Body:       string(responseBody),
		StatusCode: 200,
	}
	return
}

func (checker *DownloadStatusChecker) Check(event *events.APIGatewayV2HTTPRequest) (responseEvent events.APIGatewayV2HTTPResponse, err error) {
	responseEvent, err = checker.checkWithFastFail(event)
	if err != nil {
		switch err := err.(type) {
		case api.ApiError:
			// errr := err.(api.BusinessError)
			responseEvent = err.ToResponseEvent()
			return responseEvent, nil
		default:
			panic(err)
		}
	}
	return
}
