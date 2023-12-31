package main

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"strings"
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
	"go.uber.org/zap"
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

type MockedBoardSearcher struct {
	validPgn string
}

func (searcher MockedBoardSearcher) Match(requestId string, board string, games []GamePgn, logger *zap.Logger) (result []string, examined int, err error) {
	for _, game := range games {
		if strings.Contains(game.Pgn, searcher.validPgn) {
			result = append(result, game.Resource)
		}
		examined++

		if len(result) >= StopSearchIfFound {
			break
		}

	}
	return

}

var mockedBoardSearcher = MockedBoardSearcher{validPgn: `e4 {[%clk 0:09:57.7]} 1... g6 {[%clk 0:09:57.3]} 2. f4 {[%clk 0:09:52.6]} 2... e6 {[%clk 0:09:49.4]} 3. Nf3 {[%clk 0:09:50.5]} 3... Nc6 {[%clk 0:09:47.1]} 4. Be2 {[%clk 0:09:33.3]} 4... Nge7 {[%clk 0:09:43.3]} 5. O-O {[%clk 0:09:30.2]} 5... d5 {[%clk 0:09:41]} 6. exd5 {[%clk 0:08:58.4]} 6... Nxd5 {[%clk 0:09:39.5]} 7. d3 {[%clk 0:08:50.1]} 7... Bd6 {[%clk 0:09:30]} 8. f5 {[%clk 0:08:08.2]} 8... exf5 {[%clk 0:09:20.6]} 9. Bh6 {[%clk 0:08:07.8]} 9... Be6 {[%clk 0:09:01.4]} 10. c4 {[%clk 0:07:47.6]} 10... Nde7 {[%clk 0:08:39]} 11. d4 {[%clk 0:07:16.9]} 11... Bd7 {[%clk 0:08:10]} 12. d5 {[%clk 0:07:00.8]} 12... Bc5+ {[%clk 0:08:08.2]} 13. Kh1 {[%clk 0:06:59.2]} 13... Na5 {[%clk 0:07:52]} 14. a3 {[%clk 0:06:49.6]} 14... Bb6 {[%clk 0:07:07.9]} 15. b4 {[%clk 0:06:21.7]} 15... Nxc4 {[%clk 0:06:50.4]} 16. Bxc4 {[%clk 0:06:19.1]} 16... c6 {[%clk 0:06:49.4]} 17. d6 {[%clk 0:06:04.1]} 17... Nd5 {[%clk 0:06:06.5]} 18. Qe1+ {[%clk 0:05:01.1]} 18... Be6 {[%clk 0:06:03.8]} 19. Nc3 {[%clk 0:04:55]} 19... Qxd6 {[%clk 0:05:41.5]} 20. Nxd5 {[%clk 0:04:38.6]} 20... cxd5 {[%clk 0:05:39.5]} 21. Bb5+ {[%clk 0:04:21.9]} 21... Ke7 {[%clk 0:05:15.1]} 22. Bg7 {[%clk 0:03:31.6]} 22... Rhf8 {[%clk 0:04:20.9]} 23. Qh4+ {[%clk 0:03:15.5]} 23... f6 {[%clk 0:04:19.7]} 24. Bxf8+ {[%clk 0:03:14.9]} 24... Rxf8 {[%clk 0:04:17.5]} 25. Qxh7+ {[%clk 0:03:13]} 25... Rf7 {[%clk 0:04:16.6]} 26. Qg8 {[%clk 0:02:36.6]} 26... Rf8 {[%clk 0:03:43.5]} 27. Qxg6 {[%clk 0:02:35.7]} 27... Bc7 {[%clk 0:03:36.9]} 28. Rae1 {[%clk 0:02:09.5]} 28... a6 {[%clk 0:03:21.1]} 29. Ba4 {[%clk 0:01:58.3]} 29... Rg8 {[%clk 0:03:07.1]} 30. Qh7+ {[%clk 0:01:32.5]} 30... Kf8 {[%clk 0:02:50.8]} 31. Qh6+ {[%clk 0:00:58.7]} 31... Kf7 {[%clk 0:02:38.5]} 32. Qh5+ {[%clk 0:00:18.8]} 32... Ke7 {[%clk 0:01:45]} 33. Rxe6+ {[%clk 0:00:17.4]} 33... Qxe6 {[%clk 0:01:28.6]} 34. Re1 {[%clk 0:00:16.7]} 34... Be5 {[%clk 0:01:03.4]} 35. Nxe5 {[%clk 0:00:15.5]} 35... fxe5 {[%clk 0:00:58.8]} 36. Qh7+ {[%clk 0:00:14.4]} 36... Qf7 {[%clk 0:00:52.7]} 37. Rxe5+ {[%clk 0:00:13.1]} 37... Kf8 {[%clk 0:00:52.4]} 38. Qh6+ {[%clk 0:00:09.6]} 38... Qg7 {[%clk 0:00:45.8]} 39. Re8+ {[%clk 0:00:07.3]} 39... Kf7 {[%clk 0:00:45.4]} 40. Qe6# {[%clk 0:00:06.2]} 1-0`}

var searchers = []BoardSearcher{mockedBoardSearcher}

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
