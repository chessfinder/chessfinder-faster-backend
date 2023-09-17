package main

import (
	"fmt"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/api"
)

type DownloadStatusResponse struct {
	DownloadRequestId string `json:"downloadRequestId"`
	Failed            int    `json:"failed"`
	Succeed           int    `json:"succeed"`
	Done              int    `json:"done"`
	Pending           int    `json:"pending"`
	Total             int    `json:"total"`
}

func DownloadRequestNotFound(downloadRequestId string) api.BusinessError {
	return api.BusinessError{
		Msg:  fmt.Sprintf("Download request %v not found", downloadRequestId),
		Code: "DOWNLOAD_REQUEST_NOT_FOUND",
	}
}

type DownloadStatusRecord struct {
	DownloadRequestId string `json:"task_id"`
	Failed            int    `json:"failed"`
	Succeed           int    `json:"succeed"`
	Done              int    `json:"done"`
	Pending           int    `json:"pending"`
	Total             int    `json:"total"`
}
