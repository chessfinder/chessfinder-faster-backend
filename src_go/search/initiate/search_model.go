package main

import (
	"fmt"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
)

type SearchRequest struct {
	Username string `json:"username"`
	Platform string `json:"platform"`
	Board    string `json:"board"`
}

type SearchResponse struct {
	SearchId string `json:"searchId"`
}

var InvalidSearchBoard = api.BusinessError{
	Code:    "INVALID_SEARCH_BOARD",
	Message: "Invalid board!",
}

func ProfileIsNotCached(username string, platform string) api.BusinessError {
	return api.BusinessError{
		Code:    "PROFILE_IS_NOT_CACHED",
		Message: fmt.Sprintf("Profile %s from %s is not cached!", username, platform),
	}
}

func NoGameAvailable(username string) api.BusinessError {
	return api.BusinessError{
		Message: fmt.Sprintf("Profile %v does not have any information about their played games!", username),
		Code:    "NO_GAME_AVAILABLE",
	}
}

var UserNameCannotBeEmpty = api.BusinessError{
	Code:    "INVALID_USERNAME",
	Message: "Username cannot be empty!",
}
