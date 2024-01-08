package searches

import (
	"encoding/base64"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
)

type SearchRecord struct {
	SearchId           string                     `dynamodbav:"search_id"`
	ConsistentSearchId ConsistentSearchId         `dynamodbav:"consistent_search_id"`
	StartAt            db.ZuluDateTime            `dynamodbav:"start_at"`
	LastExaminedAt     db.ZuluDateTime            `dynamodbav:"last_examined_at"`
	Examined           int                        `dynamodbav:"examined"`
	Total              int                        `dynamodbav:"total"`
	Matched            []string                   `dynamodbav:"matched,stringset"`
	Status             SearchStatus               `dynamodbav:"status"`
	ExpiresAt          dynamodbattribute.UnixTime `dynamodbav:"expires_at"`
}

type SearchStatus string

const (
	InProgress        SearchStatus = "IN_PROGRESS"
	SearchedAll       SearchStatus = "SEARCHED_ALL"
	SearchedPartially SearchStatus = "SEARCHED_PARTIALLY"
)

func NewSearchRecord(searchId string, consistentSearchId ConsistentSearchId, startAt time.Time, downlaodedGames int, expiresIn time.Duration) SearchRecord {
	expiresAt := startAt.Add(expiresIn)
	return SearchRecord{
		SearchId:           searchId,
		ConsistentSearchId: consistentSearchId,
		StartAt:            db.Zuludatetime(startAt),
		LastExaminedAt:     db.Zuludatetime(startAt),
		Examined:           0,
		Total:              downlaodedGames,
		Matched:            []string{},
		Status:             InProgress,
		ExpiresAt:          dynamodbattribute.UnixTime(expiresAt),
	}
}

type ConsistentSearchId struct {
	value string
}

func (id ConsistentSearchId) String() string { return id.value }

func NewConsistentSearchId(userId string, downloadId string, board string) ConsistentSearchId {
	data := userId + "|" + downloadId + "|" + board
	id := base64.StdEncoding.EncodeToString([]byte(data))
	return ConsistentSearchId{value: id}
}

func (id ConsistentSearchId) MarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	av.S = aws.String(id.String())
	return nil
}

func (e *ConsistentSearchId) UnmarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	if av.S == nil || *av.S == "" {
		return nil
	}
	e.value = *av.S
	return nil
}
