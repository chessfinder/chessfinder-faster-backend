package main

import (
	"fmt"
	"time"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
)

type SearchStatus string

const (
	InProgress        SearchStatus = "IN_PROGRESS"
	SearchedAll       SearchStatus = "SEARCHED_ALL"
	SearchedPartially SearchStatus = "SEARCHED_PARTIALLY"
)

type SearchResultResponse struct {
	SearchId       string       `json:"searchId"`
	StartAt        time.Time    `json:"startAt"`
	LastExaminedAt time.Time    `json:"lastExaminedAt"`
	Examined       int          `json:"examined"`
	Total          int          `json:"total"`
	Matched        []string     `json:"matched"`
	Status         SearchStatus `json:"status"`
}

func SearchNotFound(searchId string) api.BusinessError {
	return api.BusinessError{
		Msg:  fmt.Sprintf("Search result %v not found", searchId),
		Code: "SEARCH_RESULT_NOT_FOUND",
	}
}
