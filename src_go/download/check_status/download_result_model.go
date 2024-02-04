package main

import (
	"fmt"
	"time"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
)

type DownloadResultResponse struct {
	DownloadId       string    `json:"downloadId"`
	StartAt          time.Time `json:"startAt"`
	LastDownloadedAt time.Time `json:"lastDownloadedAt"`
	Failed           int       `json:"failed"`
	Succeed          int       `json:"succeed"`
	Done             int       `json:"done"`
	Pending          int       `json:"pending"`
	Total            int       `json:"total"`
}

func DownloadNotFound(downloadId string) api.BusinessError {
	return api.BusinessError{
		Message: fmt.Sprintf("Download request %v not found", downloadId),
		Code:    "DOWNLOAD_REQUEST_NOT_FOUND",
	}
}
