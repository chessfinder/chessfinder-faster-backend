package main

import (
	"encoding/json"
	"errors"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/search/process/searcher"
	"go.uber.org/zap"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/lambda"
)

type BoardSearcher interface {
	Match(requestId string, board string, games []GamePgn, logger *zap.Logger) ([]string, int, error)
}

type GamePgn struct {
	Resource string `json:"resource"`
	Pgn      string `json:"pgn"`
}

type DirectBoardSearcher struct{}

func (s DirectBoardSearcher) Match(requestId string, board string, games []GamePgn, logger *zap.Logger) (result []string, examined int, err error) {
	examined = 0
	for _, game := range games {
		isFound := false
		isFound, err = searcher.SearchBoard(board, game.Pgn)
		if err != nil {
			return
		}
		examined++
		if isFound {
			result = append(result, game.Resource)
		}
		if len(result) >= StopSearchIfFound {
			break
		}
	}

	return
}

type DelegatedBoardSearcher struct {
	FunctionName string
	AwsConfig    *aws.Config
}

type SearchCommand struct {
	Board     string    `json:"board"`
	Games     []GamePgn `json:"games"`
	RequestId string    `json:"requestId"`
}

type SearchResponse struct {
	Examined             int      `json:"examined"`
	RequestId            string   `json:"requestId"`
	MatchedGameResources []string `json:"matchedGameResources"`
}

func (searcher DelegatedBoardSearcher) Match(requestId string, board string, games []GamePgn, logger *zap.Logger) (matchingResult []string, examined int, err error) {
	logger = logger.With(zap.String("functionName", searcher.FunctionName))

	awsSession, err := session.NewSession(searcher.AwsConfig)
	if err != nil {
		logger.Error("impossible to create an AWS session!")
		return
	}

	lambdaClient := lambda.New(awsSession)

	command := SearchCommand{
		Board:     board,
		Games:     games,
		RequestId: requestId,
	}
	payload, err := json.Marshal(command)
	if err != nil {
		logger.Error("Couldn't marshal parameters to JSON", zap.Error(err))
		return
	}

	invokeOutput, err := lambdaClient.Invoke(&lambda.InvokeInput{
		FunctionName: aws.String(searcher.FunctionName),
		Payload:      payload,
	})
	if err != nil {
		logger.Error("Couldn't invoke function", zap.Error(err))
		return
	}

	if invokeOutput.FunctionError != nil {
		logger.Error("Function returned error", zap.String("functionError", *invokeOutput.FunctionError))
		err = errors.New(*invokeOutput.FunctionError)
		return
	}

	var response SearchResponse
	err = json.Unmarshal(invokeOutput.Payload, &response)
	if err != nil {
		logger.Error("Couldn't unmarshal response", zap.Error(err))
		return
	}

	matchingResult = response.MatchedGameResources
	return
}
