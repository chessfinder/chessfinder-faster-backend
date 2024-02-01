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

	downloadsTableName, downloadsTableNameExists := os.LookupEnv("DOWNLOADS_TABLE_NAME")
	if !downloadsTableNameExists {
		panic(errors.New("DOWNLOADS_TABLE_NAME is missing"))
	}

	downloadsByConsistentDownloadIdIndexName, downloadsByConsistentDownloadIdIndexNameExists := os.LookupEnv("DOWNLOADS_BY_CONSISTENT_DOWNLOAD_ID_INDEX_NAME")
	if !downloadsByConsistentDownloadIdIndexNameExists {
		panic(errors.New("DOWNLOADS_BY_CONSISTENT_DOWNLOAD_ID_INDEX_NAME is missing"))
	}

	archivesTableName, archivesTableNameExists := os.LookupEnv("ARCHIVES_TABLE_NAME")
	if !archivesTableNameExists {
		panic(errors.New("ARCHIVES_TABLE_NAME is missing"))
	}

	usersTableName, usersTableNameExists := os.LookupEnv("USERS_TABLE_NAME")
	if !usersTableNameExists {
		panic(errors.New("USERS_TABLE_NAME is missing"))
	}

	chessDotComUrl, chessDotComUrlExists := os.LookupEnv("CHESS_DOT_COM_URL")
	if !chessDotComUrlExists {
		panic(errors.New("CHESS_DOT_COM_URL is missing"))
	}

	downloadGamesQueueUrl, downloadGamesQueueUrlExists := os.LookupEnv("DOWNLOAD_GAMES_QUEUE_URL")
	if !downloadGamesQueueUrlExists {
		panic(errors.New("DOWNLOAD_GAMES_QUEUE_URL is missing"))
	}

	downloadInfoExpiresInCadidate, downloadInfoExpiresInExists := os.LookupEnv("DOWNLOAD_INFO_EXPIRES_IN_SECONDS")
	if !downloadInfoExpiresInExists {
		panic(errors.New("DOWNLOAD_INFO_EXPIRES_IN_SECONDS is missing"))
	}

	downloadInfoExpiresIn, err := time.ParseDuration(downloadInfoExpiresInCadidate + "s")
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

	checker := ArchiveDownloader{
		downloadsTableName:                       downloadsTableName,
		downloadsByConsistentDownloadIdIndexName: downloadsByConsistentDownloadIdIndexName,
		usersTableName:                           usersTableName,
		archivesTableName:                        archivesTableName,
		downloadGamesQueueUrl:                    downloadGamesQueueUrl,
		chessDotComUrl:                           chessDotComUrl,
		metricsNamespace:                         theStackName,
		downloadInfoExpiresIn:                    downloadInfoExpiresIn,
		awsConfig: &aws.Config{
			Region: &awsRegion,
		},
	}

	lambda.Start(api.WithRecover(checker.DownloadArchiveAndDistributeDownloadGameCommands))
}
