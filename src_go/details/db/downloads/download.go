package downloads

import (
	"crypto/sha256"
	"encoding/hex"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
)

type DownloadRecord struct {
	DownloadId       DownloadId                 `dynamodbav:"download_id"`
	StartAt          db.ZuluDateTime            `dynamodbav:"start_at"`
	LastDownloadedAt db.ZuluDateTime            `dynamodbav:"last_downloaded_at"`
	ExpiresAt        dynamodbattribute.UnixTime `dynamodbav:"expires_at"`
	Succeed          int                        `dynamodbav:"succeed"`
	Failed           int                        `dynamodbav:"failed"`
	Done             int                        `dynamodbav:"done"`
	Pending          int                        `dynamodbav:"pending"`
	Total            int                        `dynamodbav:"total"`
}

func NewDownloadRecord(consistentId DownloadId, total int, startAt time.Time, expiresIn time.Duration) DownloadRecord {
	expiresAt := startAt.Add(expiresIn)
	return DownloadRecord{
		DownloadId:       consistentId,
		StartAt:          db.Zuludatetime(startAt),
		LastDownloadedAt: db.Zuludatetime(startAt),
		Failed:           0,
		Succeed:          0,
		Done:             0,
		Pending:          total,
		Total:            total,
		ExpiresAt:        dynamodbattribute.UnixTime(expiresAt),
	}
}

type DownloadId struct {
	value string
}

func (id DownloadId) String() string { return id.value }

func NewDownloadId(userId string) DownloadId {
	hash := sha256.Sum256([]byte(userId))
	id := hex.EncodeToString(hash[:])
	return DownloadId{value: id}
}

func (id DownloadId) MarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	av.S = aws.String(id.String())
	return nil
}

func (e *DownloadId) UnmarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	if av.S == nil || *av.S == "" {
		return nil
	}
	e.value = *av.S
	return nil
}
