package queue

type SearchBoardCommand struct {
	SearchId string `json:"searchId"`
	Board    string `json:"board"`
	UserId   string `json:"userId"`
}
