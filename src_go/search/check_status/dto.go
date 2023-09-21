package main

import (
	"fmt"
	"time"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/api"
)

type SearchStatus string

const (
	InProgress        = "IN_PROGRESS"
	SearchedAll       = "SEARCHED_ALL"
	SearchedPartially = "SEARCHED_PARTIALLY"
)

// const (
// 	Unknown SearchStatus = iota
// 	InProgress
// 	SearchedAll
// 	SearchedPartially
// )

// func (status SearchStatus) String() string {
// 	switch status {
// 	case InProgress:
// 		return "IN_PROGRESS"
// 	case SearchedAll:
// 		return "SEARCHED_ALL"
// 	case SearchedPartially:
// 		return "SEARCHED_PARTIALLY"
// 	default:
// 		return "UNKNOWN"
// 	}
// }

// func (status SearchStatus) MarshalJSON() ([]byte, error) {
// 	return json.Marshal(status.String())
// }

// func (status *SearchStatus) UnmarshalJSON(data []byte) error {
// 	var statusString string
// 	if err := json.Unmarshal(data, &statusString); err != nil {
// 		return err
// 	}
// 	switch statusString {
// 	case "IN_PROGRESS":
// 		*status = InProgress
// 	case "SEARCHED_ALL":
// 		*status = SearchedAll
// 	case "SEARCHED_PARTIALLY":
// 		*status = SearchedPartially
// 	default:
// 		*status = Unknown
// 	}
// 	return nil
// }

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

type SearchResultRecord struct {
	SearchRequestId string       `json:"search_request_id"`
	StartSearchAt   time.Time    `json:"start_search_at"`
	LastExaminedAt  time.Time    `json:"last_examined_at"`
	Examined        int64        `json:"examined"`
	Total           int64        `json:"total"`
	Matched         []string     `json:"matched"`
	Status          SearchStatus `json:"status"`
}
