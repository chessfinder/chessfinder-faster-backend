package main

import (
	"time"
)

type SearchResultRecord struct {
	SearchRequestId string       `json:"search_request_id"`
	StartSearchAt   time.Time    `json:"start_search_at"`
	LastExaminedAt  time.Time    `json:"last_examined_at"`
	Examined        int64        `json:"examined"`
	Total           int64        `json:"total"`
	Matched         []string     `json:"matched"`
	Status          SearchStatus `json:"status"`
}
