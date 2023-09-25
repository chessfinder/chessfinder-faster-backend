package main

type ArchiveRecord struct {
	UserId     string `json:"user_id"`
	ArchiveId  string `json:"archive_id"`
	Downloaded int    `json:"downloaded"`
}
