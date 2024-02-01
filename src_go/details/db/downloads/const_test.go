package downloads

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
)

const downloadsTableName = "chessfinder_dynamodb-downloads"

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

var awsSession = session.Must(session.NewSession(&awsConfig))

var dynamodbClient = dynamodb.New(awsSession)

var downloadsTable = DownloadsTable{
	Name:           downloadsTableName,
	DynamodbClient: dynamodbClient,
}

var downloadsByConsistentDownloadIdIndex = DownloadsByConsistentDownloadIdIndex{
	Name:           "chessfinder_dynamodb-downloadsByConsistentDownloadId",
	TableName:      downloadsTableName,
	DynamodbClient: dynamodbClient,
}
