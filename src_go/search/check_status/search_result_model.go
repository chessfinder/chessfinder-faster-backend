package main

import (
	"fmt"
	"time"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/api"
)

type SearchStatus string

const (
	InProgress        SearchStatus = "IN_PROGRESS"
	SearchedAll       SearchStatus = "SEARCHED_ALL"
	SearchedPartially SearchStatus = "SEARCHED_PARTIALLY"
)

type SearchResultResponse struct {
	SearchRequestId string       `json:"searchRequestId"`
	StartSearchAt   time.Time    `json:"startSearchAt"`
	LastExaminedAt  time.Time    `json:"lastExaminedAt"`
	Examined        int64        `json:"examined"`
	Total           int64        `json:"total"`
	Matched         []string     `json:"matched"`
	Status          SearchStatus `json:"status"`
}

func SearchResultNotFound(searchResultId string) api.BusinessError {
	return api.BusinessError{
		Msg:  fmt.Sprintf("Search result %v not found", searchResultId),
		Code: "SEARCH_RESULT_NOT_FOUND",
	}
}
