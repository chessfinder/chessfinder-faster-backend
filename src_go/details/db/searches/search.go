package searches

import (
	"time"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
)

type SearchRecord struct {
	SearchId       string          `dynamodbav:"search_id"`
	StartAt        db.ZuluDateTime `dynamodbav:"start_at"`
	LastExaminedAt db.ZuluDateTime `dynamodbav:"last_examined_at"`
	Examined       int             `dynamodbav:"examined"`
	Total          int             `dynamodbav:"total"`
	Matched        []string        `dynamodbav:"matched,stringset"`
	Status         SearchStatus    `dynamodbav:"status"`
}

type SearchStatus string

const (
	InProgress        SearchStatus = "IN_PROGRESS"
	SearchedAll       SearchStatus = "SEARCHED_ALL"
	SearchedPartially SearchStatus = "SEARCHED_PARTIALLY"
)

func NewSearchRecord(searchId string, searchAt time.Time, downlaodedGames int) SearchRecord {
	return SearchRecord{
		SearchId:       searchId,
		StartAt:        db.Zuludatetime(searchAt),
		LastExaminedAt: db.Zuludatetime(searchAt),
		Examined:       0,
		Total:          downlaodedGames,
		Matched:        []string{},
		Status:         InProgress,
	}
}
