package main

import (
	"fmt"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/api"
)

type DownloadStatusResponse struct {
	DownloadId string `json:"downloadId"`
	Failed     int    `json:"failed"`
	Succeed    int    `json:"succeed"`
	Done       int    `json:"done"`
	Pending    int    `json:"pending"`
	Total      int    `json:"total"`
}

func DownloadNotFound(downloadId string) api.BusinessError {
	return api.BusinessError{
		Msg:  fmt.Sprintf("Download request %v not found", downloadId),
		Code: "DOWNLOAD_REQUEST_NOT_FOUND",
	}
}
