package searches

import (
	"strconv"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
)

type SearchesTable struct {
	Name           string
	DynamodbClient *dynamodb.DynamoDB
}

func (table SearchesTable) PutSearchRecord(searchRecord SearchRecord) (err error) {
	items, err := dynamodbattribute.MarshalMap(searchRecord)
	if err != nil {
		return
	}

	_, err = table.DynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(table.Name),
		Item:      items,
	})

	return
}

func (table SearchesTable) GetSearchRecord(searchId string) (searchRecord *SearchRecord, err error) {
	items, err := table.DynamodbClient.GetItem(&dynamodb.GetItemInput{
		TableName: aws.String(table.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"search_id": {
				S: aws.String(searchId),
			},
		},
	})
	if err != nil {
		return
	}

	if len(items.Item) == 0 {
		return
	}

	searchRecordCadidate := SearchRecord{}
	err = dynamodbattribute.UnmarshalMap(items.Item, &searchRecordCadidate)
	if err != nil {
		return
	}

	searchRecord = &searchRecordCadidate
	return
}

func (table SearchesTable) UpdateMatchings(searchId string, examined int, matched []string, now db.ZuluDateTime) (err error) {
	var totalMatchedDynamodbAttribute *dynamodb.AttributeValue
	if len(matched) > 0 {
		totalMatchedDynamodbAttribute = &dynamodb.AttributeValue{
			SS: aws.StringSlice(matched),
		}
	} else {
		totalMatchedDynamodbAttribute = &dynamodb.AttributeValue{
			NULL: aws.Bool(true),
		}
	}
	_, err = table.DynamodbClient.UpdateItem(&dynamodb.UpdateItemInput{
		TableName: aws.String(table.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"search_id": {
				S: aws.String(searchId),
			},
		},
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":examined": {
				N: aws.String(strconv.Itoa(examined)),
			},
			":lastExaminedAt": {
				S: aws.String(now.String()),
			},
			":matched": totalMatchedDynamodbAttribute,
		},
		UpdateExpression: aws.String("SET examined = :examined, last_examined_at = :lastExaminedAt, matched = :matched"),
	})

	return
}

func (table SearchesTable) UpdateStatus(searchId string, status SearchStatus) (err error) {
	_, err = table.DynamodbClient.UpdateItem(&dynamodb.UpdateItemInput{
		TableName: aws.String(table.Name),
		Key: map[string]*dynamodb.AttributeValue{
			"search_id": {
				S: aws.String(searchId),
			},
		},
		ExpressionAttributeNames: map[string]*string{
			"#status": aws.String("status"),
		},
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":status": {
				S: aws.String(string(status)),
			},
		},
		UpdateExpression: aws.String("SET #status = :status"),
	})

	return
}
