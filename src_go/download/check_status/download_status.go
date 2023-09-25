package main

type DownloadStatusRecord struct {
	DownloadRequestId string `json:"task_id"`
	Failed            int    `json:"failed"`
	Succeed           int    `json:"succeed"`
	Done              int    `json:"done"`
	Pending           int    `json:"pending"`
	Total             int    `json:"total"`
}
