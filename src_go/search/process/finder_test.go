package main

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"testing"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/games"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/searches"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/wiremock/go-wiremock"
)

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

var finder = BoardFinder{
	searchesTableName: "chessfinder_dynamodb-searches",
	gamesTableName:    "chessfinder_dynamodb-games",
	awsConfig:         &awsConfig,
}

var awsSession = session.Must(session.NewSession(&awsConfig))
var dynamodbClient = dynamodb.New(awsSession)
var wiremockClient = wiremock.NewClient("http://0.0.0.0:18443")
var searchers = []BoardSearcher{DirectBoardSearcher{}}

var searchesTable = searches.SearchesTable{
	Name:           finder.searchesTableName,
	DynamodbClient: dynamodbClient,
}

var gamesTable = games.GamesTable{
	Name:           finder.gamesTableName,
	DynamodbClient: dynamodbClient,
}

func Test_when_there_is_a_registered_search_BoardFinder_should_look_through_all_games(t *testing.T) {
	for _, searcher := range searchers {
		finder.searcher = searcher
		func() {
			defer wiremockClient.Reset()

			if !testing.Short() {
				t.Skip("skipping test in short mode.")
			}

			startOfTest := time.Now().UTC()

			var err error
			userId := uuid.New().String()
			searchId := uuid.New().String()
			total := 0

			// if gameRecords, err := loadGameRecords(userId, searchId, "testdata/2022-07.json"); assert.NoError(t, err) {
			// 	err = finder.persistGameRecords(gameRecords)
			// 	assert.NoError(t, err)
			// 	total += len(gameRecords)
			// }

			// if gameRecords, err := loadGameRecords(userId, searchId, "testdata/2022-08.json"); assert.NoError(t, err) {
			// 	err = finder.persistGameRecords(gameRecords)
			// 	assert.NoError(t, err)
			// 	total += len(gameRecords)
			// }

			// if gameRecords, err := loadGameRecords(userId, searchId, "testdata/2022-09.json"); assert.NoError(t, err) {
			// 	err = finder.persistGameRecords(gameRecords)
			// 	assert.NoError(t, err)
			// 	total += len(gameRecords)

			// }

			if gameRecords, err := loadGameRecords(userId, searchId, "testdata/2022-10.json"); assert.NoError(t, err) {
				err = gamesTable.PutGameRecords(gameRecords)
				assert.NoError(t, err)
				total += len(gameRecords)

			}

			if gameRecords, err := loadGameRecords(userId, searchId, "testdata/2022-11.json"); assert.NoError(t, err) {
				err = gamesTable.PutGameRecords(gameRecords)
				assert.NoError(t, err)
				total += len(gameRecords)
			}

			searchAtartAt := db.Zuludatetime(startOfTest.Add(-1 * time.Hour))

			searchRecord := searches.SearchRecord{
				SearchId:       searchId,
				StartAt:        searchAtartAt,
				LastExaminedAt: searchAtartAt,
				Examined:       0,
				Total:          total,
				Matched:        []string{},
				Status:         searches.InProgress,
			}

			err = searches.SearchesTable{
				Name:           finder.searchesTableName,
				DynamodbClient: dynamodbClient,
			}.PutSearchRecord(searchRecord)

			assert.NoError(t, err)

			command :=
				events.SQSMessage{
					Body: fmt.Sprintf(
						`
						{
							"searchId": "%s",
							"board": "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????",
							"userId": "%s"
						}
					`,
						searchId,
						userId,
					),
					MessageId: "1",
				}

			actualCommandsProcessed, err := finder.Find(events.SQSEvent{Records: []events.SQSMessage{command}})
			assert.NoError(t, err)
			expectedCommandsProcessed := events.SQSEventResponse{
				BatchItemFailures: nil,
			}
			assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

			actualSearchRecord, err := searchesTable.GetSearchRecord(searchId)
			assert.NoError(t, err)

			startOfChecking := time.Now().UTC()

			assert.True(t, startOfChecking.After(actualSearchRecord.LastExaminedAt.ToTime()))
			assert.True(t, startOfTest.Before(actualSearchRecord.LastExaminedAt.ToTime()))
			assert.Equal(t, total, actualSearchRecord.Examined)
			assert.Equal(t, total, actualSearchRecord.Total)
			assert.Equal(t, searches.SearchedAll, actualSearchRecord.Status)

			assert.ElementsMatch(t, []string{"https://www.chess.com/game/live/63025767719"}, actualSearchRecord.Matched)
		}()
	}
}

func Test_when_there_is_no_registered_search_BoardFinder_should_skip(t *testing.T) {
	for _, searcher := range searchers {
		finder.searcher = searcher
		func() {
			defer wiremockClient.Reset()

			if !testing.Short() {
				t.Skip("skipping test in short mode.")
			}

			var err error
			userId := uuid.New().String()
			searchId := uuid.New().String()

			if gameRecords, err := loadGameRecords(userId, searchId, "testdata/2022-07_very_few_games.json"); assert.NoError(t, err) {
				err = gamesTable.PutGameRecords(gameRecords)
				assert.NoError(t, err)
			}

			command :=
				events.SQSMessage{
					Body: fmt.Sprintf(
						`
						{
							"searchId": "%s",
							"board": "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????",
							"userId": "%s"
						}
					`,
						searchId,
						userId,
					),
					MessageId: "1",
				}

			actualCommandsProcessed, err := finder.Find(events.SQSEvent{Records: []events.SQSMessage{command}})
			assert.NoError(t, err)
			assert.Nil(t, actualCommandsProcessed.BatchItemFailures)

			actualSearchRecord, err := searchesTable.GetSearchRecord(searchId)
			assert.NoError(t, err)
			assert.Nil(t, actualSearchRecord)
		}()
	}
}

func Test_when_there_are_more_then_10_games_that_have_the_same_position_BoardFinder_should_stop(t *testing.T) {
	for _, searcher := range searchers {
		finder.searcher = searcher
		func() {
			defer wiremockClient.Reset()

			if !testing.Short() {
				t.Skip("skipping test in short mode.")
			}

			startOfTest := time.Now().UTC()

			var err error
			userId := uuid.New().String()
			searchId := uuid.New().String()
			total := 0

			if gameRecords, err := loadGameRecords(userId, searchId, "testdata/2022-07_repeating_games.json"); assert.NoError(t, err) {
				err = gamesTable.PutGameRecords(gameRecords)
				assert.NoError(t, err)
				total += len(gameRecords)
			}

			if gameRecords, err := loadGameRecords(userId, searchId, "testdata/2022-08.json"); assert.NoError(t, err) {
				err = gamesTable.PutGameRecords(gameRecords)
				assert.NoError(t, err)
				total += len(gameRecords)
			}

			searchAtartAt := db.Zuludatetime(startOfTest.Add(-1 * time.Hour))

			searchRecord := searches.SearchRecord{
				SearchId:       searchId,
				StartAt:        searchAtartAt,
				LastExaminedAt: searchAtartAt,
				Examined:       0,
				Total:          total,
				Matched:        []string{},
				Status:         searches.InProgress,
			}

			err = searches.SearchesTable{
				Name:           finder.searchesTableName,
				DynamodbClient: dynamodbClient,
			}.PutSearchRecord(searchRecord)
			assert.NoError(t, err)

			command :=
				events.SQSMessage{
					Body: fmt.Sprintf(
						`
						{
							"searchId": "%s",
							"board": "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????",
							"userId": "%s"
						}
					`,
						searchId,
						userId,
					),
					MessageId: "1",
				}

			actualCommandsProcessed, err := finder.Find(events.SQSEvent{Records: []events.SQSMessage{command}})
			assert.NoError(t, err)
			expectedCommandsProcessed := events.SQSEventResponse{
				BatchItemFailures: nil,
			}
			assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

			actualSearchRecord, err := searchesTable.GetSearchRecord(searchId)
			assert.NoError(t, err)

			startOfChecking := time.Now().UTC()

			assert.True(t, startOfChecking.After(actualSearchRecord.LastExaminedAt.ToTime()))
			assert.True(t, startOfTest.Before(actualSearchRecord.LastExaminedAt.ToTime()))
			assert.Equal(t, StopSearchIfFound, actualSearchRecord.Examined)
			assert.Equal(t, total, actualSearchRecord.Total)
			assert.Equal(t, searches.SearchedPartially, actualSearchRecord.Status)

			expectedMatchedGames := []string{
				"https://www.chess.com/game/live/52659611873",
				"https://www.chess.com/game/live/52660795633",
				"https://www.chess.com/game/live/52661955709",
				"https://www.chess.com/game/live/52662068301",
				"https://www.chess.com/game/live/52663147917",
				"https://www.chess.com/game/live/52669789117",
				"https://www.chess.com/game/live/52670386207",
				"https://www.chess.com/game/live/52670970713",
				"https://www.chess.com/game/live/52671571319",
				"https://www.chess.com/game/live/52671679953",
			}
			assert.ElementsMatch(t, expectedMatchedGames, actualSearchRecord.Matched)
		}()
	}
}

func loadGameRecords(userId string, archiveId string, fileRelativePath string) (gameRecords []games.GameRecord, err error) {

	file, err := os.Open(fileRelativePath)
	if err != nil {
		return
	}
	defer file.Close()

	data, err := io.ReadAll(file)
	if err != nil {
		return
	}
	allGamesJson := GamesJson{}
	err = json.Unmarshal(data, &allGamesJson)
	if err != nil {
		return
	}

	for _, gameJson := range allGamesJson.Games {
		gameRecord := games.GameRecord{
			UserId:       userId,
			ArchiveId:    archiveId,
			GameId:       gameJson.Url,
			Resource:     gameJson.Url,
			Pgn:          gameJson.Pgn,
			EndTimestamp: gameJson.EndTime,
		}

		gameRecords = append(gameRecords, gameRecord)

	}
	return
}

type GamesJson struct {
	Games []GameJson `json:"games"`
}

type GameJson struct {
	Url     string `json:"url"`
	Pgn     string `json:"pgn"`
	EndTime int64  `json:"end_time"`
}
