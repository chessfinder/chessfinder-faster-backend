package archives

import (
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
)

type ArchiveRecord struct {
	UserId       string           `dynamodbav:"user_id"`
	ArchiveId    string           `dynamodbav:"archive_id"`
	Resource     string           `dynamodbav:"resource"`
	Year         int              `dynamodbav:"year"`
	Month        int              `dynamodbav:"month"`
	Downloaded   int              `dynamodbav:"downloaded"`
	DownloadedAt *db.ZuluDateTime `dynamodbav:"downloaded_at"`
}
