package main

type ChessDotComGames struct {
	Games []ChessDotComGame `json:"games"`
}

type ChessDotComGame struct {
	Url     string `json:"url"`
	Pgn     string `json:"pgn"`
	EndTime int64  `json:"end_time"`
}
