package main

import (
	"errors"
	"os"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
)

func main() {
	searchesTableName, searchesTableNameExists := os.LookupEnv("SEARCHES_TABLE_NAME")
	if !searchesTableNameExists {
		panic(errors.New("SEARCHES_TABLE_NAME is missing"))
	}

	awsRegion, awsRegionExists := os.LookupEnv("AWS_REGION")
	if !awsRegionExists {
		panic(errors.New("AWS_REGION is missing"))
	}

	checker := SearchResultChecker{
		searchesTableName: searchesTableName,
		awsConfig: &aws.Config{
			Region: &awsRegion,
		},
	}

	lambda.Start(api.WithRecover(checker.Check))
}
