package main

import (
	"errors"
	"os"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go/aws"
)

func main() {

	chessDotComUrl, chessDotComUrlExists := os.LookupEnv("CHESS_DOT_COM_URL")
	if !chessDotComUrlExists {
		panic(errors.New("CHESS_DOT_COM_URL is missing"))
	}

	downloadsTableName, downloadsTableNameExists := os.LookupEnv("DOWNLOADS_TABLE_NAME")
	if !downloadsTableNameExists {
		panic(errors.New("DOWNLOADS_TABLE_NAME is missing"))
	}

	archivesTableName, archivesTableNameExists := os.LookupEnv("ARCHIVES_TABLE_NAME")
	if !archivesTableNameExists {
		panic(errors.New("ARCHIVES_TABLE_NAME is missing"))
	}

	gamesTableName, gamesTableNameExists := os.LookupEnv("GAMES_TABLE_NAME")
	if !gamesTableNameExists {
		panic(errors.New("GAMES_TABLE_NAME is missing"))
	}

	gamesByEndTimestampIndexName, gamesByEndTimestampIndexNameExists := os.LookupEnv("GAMES_BY_END_TIMESTAMP_INDEX_NAME")
	if !gamesByEndTimestampIndexNameExists {
		panic(errors.New("GAMES_BY_END_TIMESTAMP_INDEX_NAME is missing"))
	}

	theStackName, theStackNameExists := os.LookupEnv("THE_STACK_NAME")
	if !theStackNameExists {
		panic(errors.New("THE_STACK_NAME is missing"))
	}

	awsRegion, awsRegionExists := os.LookupEnv("AWS_REGION")
	if !awsRegionExists {
		panic(errors.New("AWS_REGION is missing"))
	}

	downloader := GameDownloader{
		chessDotComUrl:               chessDotComUrl,
		downloadsTableName:           downloadsTableName,
		archivesTableName:            archivesTableName,
		gamesTableName:               gamesTableName,
		gamesByEndTimestampIndexName: gamesByEndTimestampIndexName,
		metricsNamespace:             theStackName,
		awsConfig: &aws.Config{
			Region: &awsRegion,
		},
	}

	lambda.Start(sealErrors(downloader.Download))

}

func sealErrors(unsafeHandling func(events.SQSEvent) (events.SQSEventResponse, error)) func(events.SQSEvent) (events.SQSEventResponse, error) {
	return func(commands events.SQSEvent) (commandsProcessed events.SQSEventResponse, err error) {
		_, _ = unsafeHandling(commands)
		return
	}
}
