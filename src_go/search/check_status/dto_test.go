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
			"searchRequestId": "searchRequestId",
			"startSearchAt": "2021-01-01T00:00:00Z",
			"lastExaminedAt": "2021-02-01T00:11:24Z",
			"examined": 15,
			"total": 100,
			"matched": ["https://www.chess.com/game/live/88704743803", "https://www.chess.com/game/live/88624306385"],
			"status": "SEARCHED_ALL"
		}
		`
	searchResultResponse := SearchResultResponse{
		SearchRequestId: "searchRequestId",
		StartSearchAt:   parseTime("2021-01-01T00:00:00Z"),
		LastExaminedAt:  parseTime("2021-02-01T00:11:24Z"),
		Examined:        15,
		Total:           100,
		Matched:         []string{"https://www.chess.com/game/live/88704743803", "https://www.chess.com/game/live/88624306385"},
		Status:          SearchedAll,
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
			"searchRequestId": "searchRequestId",
			"startSearchAt": "2021-01-01T00:00:00Z",
			"lastExaminedAt": "2021-02-01T00:11:24Z",
			"examined": 15,
			"total": 100,
			"matched": ["https://www.chess.com/game/live/88704743803", "https://www.chess.com/game/live/88624306385"],
			"status": "SEARCHED_ALL"
		}
		`
	expectedSearchResultResponse := SearchResultResponse{
		SearchRequestId: "searchRequestId",
		StartSearchAt:   parseTime("2021-01-01T00:00:00Z"),
		LastExaminedAt:  parseTime("2021-02-01T00:11:24Z"),
		Examined:        15,
		Total:           100,
		Matched:         []string{"https://www.chess.com/game/live/88704743803", "https://www.chess.com/game/live/88624306385"},
		Status:          SearchedAll,
	}

	actualSearchResultResponse := new(SearchResultResponse)
	err := json.Unmarshal([]byte(searchResultJson), actualSearchResultResponse)
	if err != nil {
		assert.FailNow(t, "failed to unmarshal search status response!")
	}

	assert.Equal(t, expectedSearchResultResponse, *actualSearchResultResponse)

}

func parseTime(timeString string) time.Time {
	time, err := time.Parse(time.RFC3339, timeString)
	if err != nil {
		panic(err)
	}
	return time
}
