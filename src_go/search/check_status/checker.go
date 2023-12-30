package main

import (
	"encoding/json"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/searches"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging"
	"go.uber.org/zap"
)

type SearchResultChecker struct {
	awsConfig         *aws.Config
	searchesTableName string
}

func (checker *SearchResultChecker) Check(event *events.APIGatewayV2HTTPRequest) (responseEvent events.APIGatewayV2HTTPResponse, err error) {

	logger := logging.MustCreateZuluTimeLogger()

	logger = logger.With(zap.String("requestId", event.RequestContext.RequestID))
	defer logger.Sync()

	awsSession, err := session.NewSession(checker.awsConfig)
	if err != nil {
		logger.Panic("impossible to create an AWS session!", zap.Error(err))
	}
	dynamodbClient := dynamodb.New(awsSession)

	method := event.RequestContext.HTTP.Method
	path := event.RequestContext.HTTP.Path

	if path != "/api/faster/board" || method != "GET" {
		logger.Error("searching status checker is attached to a wrong route!")
		logger.Panic("not supported")
	}

	searchId, searchIdExists := event.QueryStringParameters["searchId"]
	if !searchIdExists {
		err = api.ValidationError{
			Msg: "query parameter searchId is missing",
		}
		return
	}

	logger = logger.With(zap.String("searchId", searchId))

	searchRecordCandidate, err := searches.SearchesTable{
		Name:           checker.searchesTableName,
		DynamodbClient: dynamodbClient,
	}.GetSearchRecord(searchId)

	if err != nil {
		logger.Error("faild to get search!")
		return
	}

	if searchRecordCandidate == nil {
		logger.Error("no search search found!")
		err = SearchNotFound(searchId)
		return
	}

	searchRecord := *searchRecordCandidate

	searchResultResponse := SearchResultResponse{
		SearchId:       searchRecord.SearchId,
		Total:          searchRecord.Total,
		StartAt:        searchRecord.StartAt.ToTime(),
		LastExaminedAt: searchRecord.LastExaminedAt.ToTime(),
		Examined:       searchRecord.Examined,
		Matched:        searchRecord.Matched,
		Status:         SearchStatus(string(searchRecord.Status)),
	}
	responseBody, err := json.Marshal(searchResultResponse)
	if err != nil {
		logger.Error("faild to marshal search response!", zap.Error(err))
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
