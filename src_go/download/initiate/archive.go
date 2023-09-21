package main

import (
	"strconv"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
)

type ArchiveStatus string

const (
	FullyDownloaded     ArchiveStatus = "FULLY_DOWNLOADED"
	PartiallyDownloaded ArchiveStatus = "PARTIALLY_DOWNLOADED"
	NotDownloaded       ArchiveStatus = "NOT_DOWNLOADED"
)

type ArchiveRecord struct {
	UserId         string        `json:"user_id"`
	ArchiveId      string        `json:"archive_id"`
	Resource       string        `json:"resource"`
	Till           time.Time     `json:"till"`
	LastGamePlayed *string       `json:"last_game_played"`
	Downloaded     int           `json:"downloaded"`
	Status         ArchiveStatus `json:"status"`
}

func (record ArchiveRecord) toPutItem(archivesTableName string) *dynamodb.PutItemInput {
	itemMap := map[string]*dynamodb.AttributeValue{
		"user_id": {
			S: &record.UserId,
		},
		"archive_id": {
			S: &record.ArchiveId,
		},
		"resource": {
			S: &record.Resource,
		},
		"till": {
			S: aws.String(record.Till.Format("2006-01-02T15:04:05.000Z")),
		},
		"downloaded": {
			N: aws.String(strconv.Itoa(record.Downloaded)),
		},
		"status": {
			S: aws.String(string(record.Status)),
		},
	}
	if record.LastGamePlayed != nil {
		itemMap["last_game_played"] = &dynamodb.AttributeValue{
			S: record.LastGamePlayed,
		}
	}
	return &dynamodb.PutItemInput{
		Item:      itemMap,
		TableName: aws.String(archivesTableName),
	}
}

type ChessDotComArchives struct {
	Archives []string `json:"archives"`
}
