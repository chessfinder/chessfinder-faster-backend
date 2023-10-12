package queue

type DownloadGamesCommand struct {
	Username   string   `json:"username"`
	UserId     string   `json:"userId"`
	Platform   Platform `json:"platform"`
	ArchiveId  string   `json:"archiveId"`
	DownloadId string   `json:"downloadId"`
}

type Platform string

const (
	ChessDotCom Platform = "CHESS_DOT_COM"
	Lichess     Platform = "LICHESS"
)
