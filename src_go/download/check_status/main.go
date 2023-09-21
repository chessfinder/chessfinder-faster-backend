package main

import (
	"errors"
	"os"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go/aws"
)

func main() {
	downloadRequestTableName, downloadRequestTableNameExists := os.LookupEnv("DOWNLOAD_REQUEST_TABLE_NAME")
	if !downloadRequestTableNameExists {
		panic(errors.New("DOWNLOAD_REQUEST_TABLE_NAME is missing"))
	}

	awsRegion, awsRegionExists := os.LookupEnv("AWS_REGION")
	if !awsRegionExists {
		panic(errors.New("AWS_REGION is missing"))
	}

	checker := DownloadStatusChecker{
		downloadRequestTableName: downloadRequestTableName,
		awsConfig: &aws.Config{
			Region: &awsRegion,
		},
	}

	lambda.Start(checker.Check)
}
