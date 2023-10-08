package main

import (
	"fmt"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
)

type DownloadRequest struct {
	Username string `json:"username"`
	Platform string `json:"platform"`
}

type DownloadResponse struct {
	DownloadId string `json:"downloadId"`
}

func NoGameAvailable(user DownloadRequest) api.BusinessError {
	return api.BusinessError{
		Msg:  fmt.Sprintf("Profile %v does not have any information about their played games!", user.Username),
		Code: "NO_GAME_AVAILABLE",
	}
}

func ProfileNotFound(user DownloadRequest) api.BusinessError {
	return api.BusinessError{
		Msg:  fmt.Sprintf("Profile %v not found!", user.Username),
		Code: "PROFILE_NOT_FOUND",
	}
}
