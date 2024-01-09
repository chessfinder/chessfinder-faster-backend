package downloads

import (
	"encoding/base64"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
)

type DownloadRecord struct {
	DownloadId           string                     `dynamodbav:"download_id"`
	ConsistentDownloadId ConsistentDownloadId       `dynamodbav:"consistent_download_id"`
	StartAt              db.ZuluDateTime            `dynamodbav:"start_at"`
	LastDownloadedAt     db.ZuluDateTime            `dynamodbav:"last_downloaded_at"`
	ExpiresAt            dynamodbattribute.UnixTime `dynamodbav:"expires_at"`
	Succeed              int                        `dynamodbav:"succeed"`
	Failed               int                        `dynamodbav:"failed"`
	Done                 int                        `dynamodbav:"done"`
	Pending              int                        `dynamodbav:"pending"`
	Total                int                        `dynamodbav:"total"`
}

func NewDownloadRecord(downloadId string, consistentId ConsistentDownloadId, total int, startAt time.Time, expiresIn time.Duration) DownloadRecord {
	expiresAt := startAt.Add(expiresIn)
	return DownloadRecord{
		DownloadId:           downloadId,
		ConsistentDownloadId: consistentId,
		StartAt:              db.Zuludatetime(startAt),
		LastDownloadedAt:     db.Zuludatetime(startAt),
		Failed:               0,
		Succeed:              0,
		Done:                 0,
		Pending:              total,
		Total:                total,
		ExpiresAt:            dynamodbattribute.UnixTime(expiresAt),
	}
}

type ConsistentDownloadId struct {
	value string
}

func (id ConsistentDownloadId) String() string { return id.value }

func NewConsistentDownloadId(userId string) ConsistentDownloadId {
	id := base64.StdEncoding.EncodeToString([]byte(userId))
	return ConsistentDownloadId{value: id}
}

func (id ConsistentDownloadId) MarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	av.S = aws.String(id.String())
	return nil
}

func (e *ConsistentDownloadId) UnmarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	if av.S == nil || *av.S == "" {
		return nil
	}
	e.value = *av.S
	return nil
}
