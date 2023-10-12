package main

import (
	"errors"
	"os"

	"github.com/aws/aws-lambda-go/events"
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

	awsRegion, awsRegionExists := os.LookupEnv("AWS_REGION")
	if !awsRegionExists {
		panic(errors.New("AWS_REGION is missing"))
	}

	finder := BoardFinder{
		searchesTableName: searchesTableName,
		gamesTableName:    gamesTableName,
		awsConfig: &aws.Config{
			Region: &awsRegion,
		},
	}

	lambda.Start(sealErrors(finder.Find))

}

func sealErrors(unsafeHandling func(events.SQSEvent) (events.SQSEventResponse, error)) func(events.SQSEvent) (events.SQSEventResponse, error) {
	return func(commands events.SQSEvent) (commandsProcessed events.SQSEventResponse, err error) {
		_, _ = unsafeHandling(commands)
		return
	}
}
