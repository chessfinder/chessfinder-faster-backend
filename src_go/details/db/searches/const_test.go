package searches

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
)

const searchesTableName = "chessfinder_dynamodb-searches"

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

var awsSession = session.Must(session.NewSession(&awsConfig))

var dynamodbClient = dynamodb.New(awsSession)

var searchesTable = SearchesTable{
	Name:           searchesTableName,
	DynamodbClient: dynamodbClient,
}

var searchesByConsistentSearchIdIndex = SearchesByConsistentSearchIdIndex{
	Name:           "chessfinder_dynamodb-searchesByConsistentSearchId",
	TableName:      searchesTableName,
	DynamodbClient: dynamodbClient,
}
