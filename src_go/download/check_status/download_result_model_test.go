package main

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func Test_Download_Result_Is_Marshalled_Correctlly(t *testing.T) {
	expectedDownloadResultJson :=
		`{
		"downloadId": "downloadRequestId",
		"startAt": "2021-01-01T00:00:00.123Z",
		"lastDownloadedAt": "2021-02-01T00:11:24Z",
		"failed": 5,
		"succeed": 2,
		"done": 7,
		"pending": 3,
		"total": 10
	}`

	startAt, err := time.Parse("2006-01-02T15:04:05.000Z", "2021-01-01T00:00:00.123Z")
	assert.NoError(t, err)
	lastExaminedAt, err := time.Parse("2006-01-02T15:04:05.000Z", "2021-02-01T00:11:24.000Z")
	assert.NoError(t, err)

	downloadResultResponse := DownloadResultResponse{
		DownloadId:       "downloadRequestId",
		StartAt:          startAt,
		LastDownloadedAt: lastExaminedAt,
		Failed:           5,
		Succeed:          2,
		Done:             7,
		Pending:          3,
		Total:            10,
	}

	actualResultStatusJson, err := json.Marshal(downloadResultResponse)
	if err != nil {
		assert.FailNow(t, "failed to marshal search status response!")
	}

	assert.JSONEq(t, expectedDownloadResultJson, string(actualResultStatusJson))

}
