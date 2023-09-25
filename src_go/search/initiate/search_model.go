package main

import (
	"fmt"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/api"
)

type SearchRequest struct {
	User     string `json:"user"`
	Platform string `json:"platform"`
	Board    string `json:"board"`
}

type SearchResponse struct {
	SearchResultId string `json:"searchResultId"`
}

var InvalidSearchBoard = api.BusinessError{
	Code: "INVALID_SEARCH_BOARD",
	Msg:  "Invalid board!",
}

func ProfileIsNotCached(username string, platform string) api.BusinessError {
	return api.BusinessError{
		Code: "PROFILE_IS_NOT_CACHED",
		Msg:  fmt.Sprintf("Profile %s from %s is not cached!", username, platform),
	}
}

func NoGameAvailable(user UserRecord) api.BusinessError {
	return api.BusinessError{
		Msg:  fmt.Sprintf("Profile %v does not have any information about their played games!", user.UserName),
		Code: "NO_GAME_AVAILABLE",
	}
}
