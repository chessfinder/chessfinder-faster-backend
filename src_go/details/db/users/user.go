package users

type UserRecord struct {
	Username            string   `dynamodbav:"username"`
	Platform            Platform `dynamodbav:"platform"`
	UserId              string   `dynamodbav:"user_id"`
	DownloadFromScratch bool     `dynamodbav:"download_from_scratch"`
}

type Platform string

const (
	ChessDotCom Platform = "CHESS_DOT_COM"
	Lichess     Platform = "LICHESS"
)
