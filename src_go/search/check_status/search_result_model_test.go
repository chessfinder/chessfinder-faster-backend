package main

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func Test_Search_Result_Is_Marshalled_Correctlly(t *testing.T) {
	expectedSearchResultJson := `
		{	
			"searchId": "searchRequestId",
			"startAt": "2021-01-01T00:00:00.123Z",
			"lastExaminedAt": "2021-02-01T00:11:24Z",
			"examined": 15,
			"total": 100,
			"matched": ["https://www.chess.com/game/live/88704743803", "https://www.chess.com/game/live/88624306385"],
			"status": "SEARCHED_ALL"
		}
		`
	startAt, err := time.Parse("2006-01-02T15:04:05.000Z", "2021-01-01T00:00:00.123Z")
	assert.NoError(t, err)
	lastExaminedAt, err := time.Parse("2006-01-02T15:04:05.000Z", "2021-02-01T00:11:24.000Z")
	assert.NoError(t, err)

	searchResultResponse := SearchResultResponse{
		SearchId:       "searchRequestId",
		StartAt:        startAt,
		LastExaminedAt: lastExaminedAt,
		Examined:       15,
		Total:          100,
		Matched:        []string{"https://www.chess.com/game/live/88704743803", "https://www.chess.com/game/live/88624306385"},
		Status:         SearchedAll,
	}

	actualResultStatusJson, err := json.Marshal(searchResultResponse)
	if err != nil {
		assert.FailNow(t, "failed to marshal search status response!")
	}

	assert.JSONEq(t, expectedSearchResultJson, string(actualResultStatusJson))

}

func Test_Search_Result_Is_Unmarshalled_Correctlly(t *testing.T) {
	searchResultJson := `
		{	
			"searchId": "searchRequestId",
			"startAt": "2021-01-01T00:00:00.000Z",
			"lastExaminedAt": "2021-02-01T00:11:24.000Z",
			"examined": 15,
			"total": 100,
			"matched": ["https://www.chess.com/game/live/88704743803", "https://www.chess.com/game/live/88624306385"],
			"status": "SEARCHED_ALL"
		}
		`
	startAt, err := time.Parse("2006-01-02T15:04:05.000Z", "2021-01-01T00:00:00.000Z")
	assert.NoError(t, err)
	lastExaminedAt, err := time.Parse("2006-01-02T15:04:05.000Z", "2021-02-01T00:11:24.000Z")
	assert.NoError(t, err)
	expectedSearchResultResponse := SearchResultResponse{
		SearchId:       "searchRequestId",
		StartAt:        startAt,
		LastExaminedAt: lastExaminedAt,
		Examined:       15,
		Total:          100,
		Matched:        []string{"https://www.chess.com/game/live/88704743803", "https://www.chess.com/game/live/88624306385"},
		Status:         SearchedAll,
	}

	actualSearchResultResponse := new(SearchResultResponse)
	err = json.Unmarshal([]byte(searchResultJson), actualSearchResultResponse)
	assert.NoError(t, err)

	assert.Equal(t, expectedSearchResultResponse, *actualSearchResultResponse)

}
