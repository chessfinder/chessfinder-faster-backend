package main

type SearchBoardCommand struct {
	UserId          string `json:"userId"`
	SearchRequestId string `json:"searchRequestId"`
	Board           string `json:"board"`
}
