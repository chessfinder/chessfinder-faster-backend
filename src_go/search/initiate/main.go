package main

import (
	"errors"
	"os"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
)

func main() {

	userTableName, userTableExists := os.LookupEnv("USERS_TABLE_NAME")
	if !userTableExists {
		panic(errors.New("USERS_TABLE_NAME is missing"))
	}

	archivesTableName, archivesTableNameExists := os.LookupEnv("ARCHIVES_TABLE_NAME")
	if !archivesTableNameExists {
		panic(errors.New("ARCHIVES_TABLE_NAME is missing"))
	}

	searchesTableName, searchesTableNameExists := os.LookupEnv("SEARCHES_TABLE_NAME")
	if !searchesTableNameExists {
		panic(errors.New("SEARCHES_TABLE_NAME is missing"))
	}

	searchBoardQueueUrl, searchBoardQueueUrlExists := os.LookupEnv("SEARCH_BOARD_QUEUE_URL")
	if !searchBoardQueueUrlExists {
		panic(errors.New("SEARCH_BOARD_QUEUE_URL is missing"))
	}

	awsRegion, awsRegionExists := os.LookupEnv("AWS_REGION")
	if !awsRegionExists {
		panic(errors.New("AWS_REGION is missing"))
	}

	registrar := SearchRegistrar{
		userTableName:       userTableName,
		archivesTableName:   archivesTableName,
		searchesTableName:   searchesTableName,
		searchBoardQueueUrl: searchBoardQueueUrl,
		awsConfig: &aws.Config{
			Region: &awsRegion,
		},
	}

	lambda.Start(api.WithRecover(registrar.RegisterSearchRequest))

}
