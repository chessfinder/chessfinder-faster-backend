package main

import (
	"fmt"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/api"
)

type DownloadRequest struct {
	User     string `json:"user"`
	Platform string `json:"platform"`
}

var InvalidBody = api.ValidationError{
	Msg: "Invalid body",
}

type DownloadResponse struct {
	TaskId string `json:"taskId"`
}

func NoGameAvailable(user DownloadRequest) api.BusinessError {
	return api.BusinessError{
		Msg:  fmt.Sprintf("Profile %v does not have any information about their played games!", user.User),
		Code: "NO_GAME_AVAILABLE",
	}
}

func ProfileNotFound(user DownloadRequest) api.BusinessError {
	return api.BusinessError{
		Msg:  fmt.Sprintf("Profile %v not found!", user.User),
		Code: "PROFILE_NOT_FOUND",
	}
}

var ServiceOverloaded = api.BusinessError{
	Msg:  "Service is overloaded. Please try again later.",
	Code: "SERVICE_OVERLOADED",
}
