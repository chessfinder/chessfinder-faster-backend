package main

import (
	"errors"
	"os"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go/aws"
)

func main() {
	searchResultTableName, searchResultTableNameExists := os.LookupEnv("SEARCH_RESULT_TABLE_NAME")
	if !searchResultTableNameExists {
		panic(errors.New("SEARCH_RESULT_TABLE_NAME is missing"))
	}

	awsRegion, awsRegionExists := os.LookupEnv("AWS_REGION")
	if !awsRegionExists {
		panic(errors.New("AWS_REGION is missing"))
	}

	checker := SearchResultChecker{
		searchResultTableName: searchResultTableName,
		awsConfig: &aws.Config{
			Region: &awsRegion,
		},
	}

	lambda.Start(checker.Check)
}
