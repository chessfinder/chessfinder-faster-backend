package downloads

type DownloadRecord struct {
	DownloadId string `dynamodbav:"download_id"`
	Succeed    int    `dynamodbav:"succeed"`
	Failed     int    `dynamodbav:"failed"`
	Done       int    `dynamodbav:"done"`
	Pending    int    `dynamodbav:"pending"`
	Total      int    `dynamodbav:"total"`
}

func NewDownloadRecord(downloadId string, total int) DownloadRecord {
	return DownloadRecord{
		DownloadId: downloadId,
		Failed:     0,
		Succeed:    0,
		Done:       0,
		Pending:    total,
		Total:      total,
	}
}
