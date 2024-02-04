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

func ProfileNotFound(user DownloadRequest) api.BusinessError {
	return api.BusinessError{
		Message: fmt.Sprintf("Profile %v not found!", user.Username),
		Code:    "PROFILE_NOT_FOUND",
	}
}

var UserNameCannotBeEmpty = api.BusinessError{
	Code:    "INVALID_USERNAME",
	Message: "Username cannot be empty!",
}
