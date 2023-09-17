package main

type DownloadGameCommand struct {
	UserName          string `json:"userName"`
	Platform          string `json:"platform"`
	UserId            string `json:"userId"`
	ArchiveId         string `json:"archiveId"`
	DownloadRequestId string `json:"taskId"`
}
