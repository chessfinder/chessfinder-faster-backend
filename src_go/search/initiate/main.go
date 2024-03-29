package main

import (
	"errors"
	"os"
	"time"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
)

func main() {

	usersTableName, usersTableExists := os.LookupEnv("USERS_TABLE_NAME")
	if !usersTableExists {
		panic(errors.New("USERS_TABLE_NAME is missing"))
	}

	archivesTableName, archivesTableNameExists := os.LookupEnv("ARCHIVES_TABLE_NAME")
	if !archivesTableNameExists {
		panic(errors.New("ARCHIVES_TABLE_NAME is missing"))
	}

	downloadsTableName, downloadsTableNameExists := os.LookupEnv("DOWNLOADS_TABLE_NAME")
	if !downloadsTableNameExists {
		panic(errors.New("DOWNLOADS_TABLE_NAME is missing"))
	}

	searchesTableName, searchesTableNameExists := os.LookupEnv("SEARCHES_TABLE_NAME")
	if !searchesTableNameExists {
		panic(errors.New("SEARCHES_TABLE_NAME is missing"))
	}

	searchBoardQueueUrl, searchBoardQueueUrlExists := os.LookupEnv("SEARCH_BOARD_QUEUE_URL")
	if !searchBoardQueueUrlExists {
		panic(errors.New("SEARCH_BOARD_QUEUE_URL is missing"))
	}

	chessfinderValidationCoreFunctionName, chessfinderValidationCoreFunctionNameExists := os.LookupEnv("CHESSFINDER_VALIDATION_CORE_FUNCTION_NAME")
	if !chessfinderValidationCoreFunctionNameExists {
		panic(errors.New("CHESSFINDER_VALIDATION_CORE_FUNCTION_NAME is missing"))
	}

	searchInfoExpiresInCadidate, searchInfoExpiresInExists := os.LookupEnv("SEARCH_INFO_EXPIRES_IN_SECONDS")
	if !searchInfoExpiresInExists {
		panic(errors.New("SEARCH_INFO_EXPIRES_IN_SECONDS is missing"))
	}

	searchInfoExpiresIn, err := time.ParseDuration(searchInfoExpiresInCadidate + "s")
	if err != nil {
		panic(err)
	}

	theStackName, theStackNameExists := os.LookupEnv("THE_STACK_NAME")
	if !theStackNameExists {
		panic(errors.New("THE_STACK_NAME is missing"))
	}

	awsRegion, awsRegionExists := os.LookupEnv("AWS_REGION")
	if !awsRegionExists {
		panic(errors.New("AWS_REGION is missing"))
	}

	awsConfig := &aws.Config{
		Region: &awsRegion,
	}

	registrar := SearchRegistrar{
		usersTableName:      usersTableName,
		archivesTableName:   archivesTableName,
		downloadsTableName:  downloadsTableName,
		searchesTableName:   searchesTableName,
		searchBoardQueueUrl: searchBoardQueueUrl,
		searchInfoExpiresIn: searchInfoExpiresIn,
		metricsNamespace:    theStackName,
		awsConfig:           awsConfig,
		validator: DelegatedBoardValidator{
			FunctionName: chessfinderValidationCoreFunctionName,
			AwsConfig:    awsConfig,
		},
	}

	lambda.Start(api.WithRecover(registrar.RegisterSearchRequest))

}
