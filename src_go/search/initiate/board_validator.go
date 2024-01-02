package main

import (
	"encoding/json"
	"errors"

	"go.uber.org/zap"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/lambda"
)

type BoardValidator interface {
	Validate(requestId string, board string, logger *zap.Logger) (bool, *string, error)
}

type DelegatedBoardValidator struct {
	FunctionName string
	AwsConfig    *aws.Config
}

type ValidationCommand struct {
	Board     string `json:"board"`
	RequestId string `json:"requestId"`
}

type ValidationResponse struct {
	IsValid   bool    `json:"isValid"`
	RequestId string  `json:"requestId"`
	Comment   *string `json:"comment"`
}

func (validator DelegatedBoardValidator) Validate(requestId string, board string, logger *zap.Logger) (isValid bool, comment *string, err error) {
	logger = logger.With(zap.String("functionName", validator.FunctionName))

	awsSession, err := session.NewSession(validator.AwsConfig)
	if err != nil {
		logger.Error("impossible to create an AWS session!")
		return
	}

	lambdaClient := lambda.New(awsSession)

	command := ValidationCommand{
		Board:     board,
		RequestId: requestId,
	}
	payload, err := json.Marshal(command)
	if err != nil {
		logger.Error("Couldn't marshal parameters to JSON", zap.Error(err))
		return
	}

	invokeOutput, err := lambdaClient.Invoke(&lambda.InvokeInput{
		FunctionName: aws.String(validator.FunctionName),
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

	var response ValidationResponse
	err = json.Unmarshal(invokeOutput.Payload, &response)
	if err != nil {
		logger.Error("Couldn't unmarshal response", zap.Error(err))
		return
	}

	isValid = response.IsValid
	comment = response.Comment
	return
}
