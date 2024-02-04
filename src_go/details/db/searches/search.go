package searches

import (
	"crypto/sha256"
	"encoding/hex"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
)

type SearchRecord struct {
	SearchId       SearchId                   `dynamodbav:"search_id"`
	StartAt        db.ZuluDateTime            `dynamodbav:"start_at"`
	LastExaminedAt db.ZuluDateTime            `dynamodbav:"last_examined_at"`
	Examined       int                        `dynamodbav:"examined"`
	Total          int                        `dynamodbav:"total"`
	Matched        []string                   `dynamodbav:"matched,stringset"`
	Status         SearchStatus               `dynamodbav:"status"`
	ExpiresAt      dynamodbattribute.UnixTime `dynamodbav:"expires_at"`
}

type SearchStatus string

const (
	InProgress        SearchStatus = "IN_PROGRESS"
	SearchedAll       SearchStatus = "SEARCHED_ALL"
	SearchedPartially SearchStatus = "SEARCHED_PARTIALLY"
)

func NewSearchRecord(searchId SearchId, startAt time.Time, downlaodedGames int, expiresIn time.Duration) SearchRecord {
	expiresAt := startAt.Add(expiresIn)
	return SearchRecord{
		SearchId:       searchId,
		StartAt:        db.Zuludatetime(startAt),
		LastExaminedAt: db.Zuludatetime(startAt),
		Examined:       0,
		Total:          downlaodedGames,
		Matched:        []string{},
		Status:         InProgress,
		ExpiresAt:      dynamodbattribute.UnixTime(expiresAt),
	}
}

type SearchId struct {
	value string
}

func (id SearchId) String() string { return id.value }

func NewSearchId(userId string, downloadStartedAt *db.ZuluDateTime, board string) SearchId {
	var data string
	if downloadStartedAt == nil {
		data = userId + "#_#" + board
	} else {
		data = userId + "#" + downloadStartedAt.String() + "#" + board
	}
	hash := sha256.Sum256([]byte(data))
	id := hex.EncodeToString(hash[:])
	return SearchId{value: id}
}

func (id SearchId) MarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	av.S = aws.String(id.String())
	return nil
}

func (e *SearchId) UnmarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	if av.S == nil || *av.S == "" {
		return nil
	}
	e.value = *av.S
	return nil
}
