package main

import "encoding/json"

type ChessDotComGames struct {
	Games []ChessDotComGame `json:"games"`
}

type ChessDotComGame struct {
	Url     string          `json:"url"`
	Pgn     json.RawMessage `json:"pgn"`
	EndTime int64           `json:"end_time"`
}
