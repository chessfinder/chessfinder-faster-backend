package main

import (
	"errors"
	"os"
	"time"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go/aws"
)

func main() {

	gamesTableName, gamesTableNameExists := os.LookupEnv("GAMES_TABLE_NAME")
	if !gamesTableNameExists {
		panic(errors.New("GAMES_TABLE_NAME is missing"))
	}

	searchesTableName, searchesTableNameExists := os.LookupEnv("SEARCHES_TABLE_NAME")
	if !searchesTableNameExists {
		panic(errors.New("SEARCHES_TABLE_NAME is missing"))
	}

	chessfinderSearchCoreFunctionName, chessfinderSearchCoreFunctionNameExists := os.LookupEnv("CHESSFINDER_SEARCH_CORE_FUNCTION_NAME")
	if !chessfinderSearchCoreFunctionNameExists {
		panic(errors.New("CHESSFINDER_SEARCH_CORE_FUNCTION_NAME is missing"))
	}

	searchInfoExpiresInCadidate, searchInfoExpiresInExists := os.LookupEnv("SEARCH_INFO_EXPIRES_IN_SECONDS")
	if !searchInfoExpiresInExists {
		panic(errors.New("SEARCH_INFO_EXPIRES_IN_SECONDS is missing"))
	}

	searchInfoExpiresIn, err := time.ParseDuration(searchInfoExpiresInCadidate + "s")
	if err != nil {
		panic(err)
	}

	awsRegion, awsRegionExists := os.LookupEnv("AWS_REGION")
	if !awsRegionExists {
		panic(errors.New("AWS_REGION is missing"))
	}

	awsConfig := &aws.Config{
		Region: &awsRegion,
	}

	finder := BoardFinder{
		searchesTableName:   searchesTableName,
		gamesTableName:      gamesTableName,
		searchInfoExpiresIn: searchInfoExpiresIn,
		awsConfig:           awsConfig,
		searcher: DelegatedBoardSearcher{
			FunctionName: chessfinderSearchCoreFunctionName,
			AwsConfig:    awsConfig,
		},
	}

	lambda.Start(finder.Find)

}
