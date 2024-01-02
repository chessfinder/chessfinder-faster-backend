package main

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"testing"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/archives"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/downloads"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/games"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/wiremock/go-wiremock"
)

var awsConfig = aws.Config{
	Region:     aws.String("us-east-1"),
	Endpoint:   aws.String("http://localhost:4566"), // this is the LocalStack endpoint for all services
	DisableSSL: aws.Bool(true),
}

var downloader = GameDownloader{
	chessDotComUrl:               "http://0.0.0.0:18443",
	downloadsTableName:           "chessfinder_dynamodb-downloads",
	archivesTableName:            "chessfinder_dynamodb-archives",
	gamesTableName:               "chessfinder_dynamodb-games",
	gamesByEndTimestampIndexName: "chessfinder_dynamodb-gamesByEndTimestamp",
	awsConfig:                    &awsConfig,
}
var awsSession = session.Must(session.NewSession(&awsConfig))
var dynamodbClient = dynamodb.New(awsSession)
var wiremockClient = wiremock.NewClient("http://0.0.0.0:18443")
var downloadsTable = downloads.DownloadsTable{
	Name:           downloader.downloadsTableName,
	DynamodbClient: dynamodbClient,
}
var gamesTable = games.GamesTable{
	Name:           downloader.gamesTableName,
	DynamodbClient: dynamodbClient,
}
var archivesTable = archives.ArchivesTable{
	Name:           downloader.archivesTableName,
	DynamodbClient: dynamodbClient,
}

func Test_when_pgn_filtering_is_on_CommitDownloader_should_download_and_persist_only_the_moves_of_the_pgn(t *testing.T) {
	defer wiremockClient.Reset()
	downloader.pgnFilter = TagAndCommentPgnFilter{}

	startOfTest := time.Now().UTC()

	var err error
	username := uuid.New().String()
	userId := uuid.New().String()
	archiveId := uuid.New().String()
	archiveResource := uuid.New().String()

	archiveRecord := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		Resource:     archiveResource,
		Year:         2022,
		Month:        8,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	err = archivesTable.PutArchiveRecord(archiveRecord)
	assert.NoError(t, err)

	newGame1 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53168947271",
		Resource:     "https://www.chess.com/game/live/53168947271",
		Pgn:          "\n1. d4 b5 2. Nc3 Bb7 3. Nxb5 a6 4. Nc3 c6 5. e3 Qb6 6. Nf3 e6 7. Bd3 Bb4 8. Bd2 1-0",
		EndTimestamp: 1659429921,
	}

	newGame2 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53168974013",
		Resource:     "https://www.chess.com/game/live/53168974013",
		Pgn:          "\n1. d4 d5 2. c4 e6 3. Nc3 dxc4 4. e3 Nf6 5. Bxc4 b6 6. Nf3 Be7 7. O-O O-O 8. b3 Nc6 9. Nb1 a5 10. a4 Ba6 11. Nbd2 Bxc4 12. Nxc4 Nb4 13. Ba3 Ne4 14. Nfd2 Nxd2 15. Nxd2 Qd5 16. Nf3 Rfe8 17. Ne1 Nc6 18. Nd3 Bxa3 19. Rxa3 Ne7 20. Nf4 Qd6 21. Qd3 Qxa3  0-1",
		EndTimestamp: 1659430140,
	}

	newGame3 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53169013011",
		Resource:     "https://www.chess.com/game/live/53169013011",
		Pgn:          "\n1. d4 g6 2. Nc3 Bg7 3. Nf3 Nc6 4. e3 d6 5. Bb5 Bd7 6. O-O a6 7. Bxc6 Bxc6 8. d5 Bxc3 9. dxc6 Bxb2 10. Bxb2 Nf6 11. Bxf6 exf6 12. cxb7 Rb8 13. Rb1 O-O 14. Rb3 Qd7 15. Qd4 c5 16. Qxf6 Rxb7 17. Rxb7 Qxb7 18. Qxd6 Rb8 19. h3 c4 20. c3 Qb2 21. Qd2 Qb6 22. Qd4 Qb2 23. Qxc4 a5 24. Ne5 Qc2 25. Qxf7+ Kh8 26. Qf6+ Kg8 27. Qe6+ Kh8 28. Nf7+ Kg8 29. Nh6+ Kg7 30. Qe5+ Kxh6 31. Qxb8 Qxa2 32. Rb1 a4 33. Rb5 Qc2 34. Qf8# 1-0",
		EndTimestamp: 1659430643,
	}

	newGame4 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53169604577",
		Resource:     "https://www.chess.com/game/live/53169604577",
		Pgn:          "\n1. e4 e5 2. f4 exf4 3. Nf3 Nf6 4. e5 Ng4 5. d4 Be7 6. Bc4 O-O 7. Bxf4 d5 8. Bd3 f6 9. O-O fxe5 10. Bxe5 Nxe5 11. Nxe5 Nd7 12. Nc3 Nxe5 13. dxe5 Bc5+ 14. Kh1 b6 15. Nxd5 Bb7 16. Nf4 Rxf4 17. Rxf4 Qg5 18. Qf1 Rf8 19. Rxf8+ Bxf8 20. Bc4+ Kh8 21. Qxf8# 1-0",
		EndTimestamp: 1659431044,
	}

	newGame5 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53170160741",
		Resource:     "https://www.chess.com/game/live/53170160741",
		Pgn:          "\n1. e4 e5 2. Qh5 Nf6 3. Qxe5+ Qe7 4. Qxe7+ Bxe7 5. d3 O-O 6. Bg5 h6 7. Bxf6 Bxf6 8. c3 c6 9. Nf3 d5 10. e5 Re8 11. d4 Bg4 12. Be2 Bxf3 13. Bxf3 Bg5 14. Na3 f6 15. O-O fxe5 16. dxe5 Rxe5 17. Rfe1 Rxe1+ 18. Rxe1 Nd7 19. Nc2 Nb6 20. Nd4 c5 21. Ne6 Bd2 22. Re2 Re8 23. Kf1 d4 24. cxd4 cxd4 25. Nxd4 Rxe2 26. Bxe2 Nd5 27. Bc4 1-0",
		EndTimestamp: 1659431342,
	}

	newGame6 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53170213967",
		Resource:     "https://www.chess.com/game/live/53170213967",
		Pgn:          "\n1. e4 e5 2. Bc4 Nf6 3. Nf3 h6 4. d4 exd4 5. Nxd4 Bc5 6. O-O O-O 7. e5 Bxd4 8. Qxd4 Nc6 9. Qf4 Ng4 10. Qxg4 d5 11. Bxh6 Bxg4  0-1",
		EndTimestamp: 1659431445,
	}

	downloadId := uuid.New().String()
	downloadRecord := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    3,
		Failed:     0,
		Done:       3,
		Pending:    2,
		Total:      5,
	}

	err = downloadsTable.PutDownloadRecord(downloadRecord)
	assert.NoError(t, err)

	stubDownload, err := downloader.stubChessDotCom(username, "2022", "08")
	assert.NoError(t, err)

	err = wiremockClient.StubFor(stubDownload)
	assert.NoError(t, err)

	command :=
		events.SQSMessage{
			Body: fmt.Sprintf(
				`
				{
					"username": "%s",
					"userId": "%s",
					"platform": "CHESS_DOT_COM",
					"archiveId": "%s",
					"downloadId": "%s",
					"hehe": "💣"
				}
			`,
				username,
				userId,
				archiveId,
				downloadId,
			),
			MessageId: "1",
		}

	actualCommandsProcessed, err := downloader.Download(events.SQSEvent{Records: []events.SQSMessage{command}})
	assert.NoError(t, err)

	expectedCommandsProcessed := events.SQSEventResponse{
		BatchItemFailures: nil,
	}
	assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

	actualArchive, err := archivesTable.GetArchiveRecord(userId, archiveId)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive)

	startOfChecking := time.Now().UTC()

	assert.Equal(t, 6, actualArchive.Downloaded)
	assert.True(t, startOfChecking.After(actualArchive.DownloadedAt.ToTime()))
	assert.True(t, startOfTest.Before(actualArchive.DownloadedAt.ToTime()))

	var noKey map[string]*dynamodb.AttributeValue
	actualGames, _, err := gamesTable.QueryGames(userId, noKey, 1000)
	assert.NoError(t, err)

	expectedGames := []games.GameRecord{
		newGame1,
		newGame2,
		newGame3,
		newGame4,
		newGame5,
		newGame6,
	}

	assert.ElementsMatch(t, expectedGames, actualGames)

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownload)

	expectedDownload := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    4,
		Failed:     0,
		Done:       4,
		Pending:    1,
		Total:      5,
	}

	assert.Equal(t, expectedDownload, *actualDownload)

	verifyDownloadedCall, err := wiremockClient.Verify(stubDownload.Request(), 1)
	assert.NoError(t, err)
	assert.True(t, verifyDownloadedCall)
}
func Test_when_archive_is_partially_downloaded_CommitDownloader_should_download_remaining_games(t *testing.T) {
	defer wiremockClient.Reset()
	downloader.pgnFilter = IdentityPgnFilter{}

	startOfTest := time.Now().UTC()

	var err error
	username := uuid.New().String()
	userId := uuid.New().String()
	archiveId := uuid.New().String()

	lastDownloadedAt := db.Zuludatetime(time.Date(2022, 8, 2, 8, 45, 21, 0, time.UTC))

	archiveRecord := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		Resource:     archiveId,
		Year:         2022,
		Month:        8,
		DownloadedAt: &lastDownloadedAt,
		Downloaded:   3,
	}

	err = archivesTable.PutArchiveRecord(archiveRecord)
	assert.NoError(t, err)

	existingGame1 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53168947271",
		Resource:     "https://www.chess.com/game/live/53168947271",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"akashvishwakarma000\"]\n[Result \"1-0\"]\n[CurrentPosition \"rn2k1nr/1b1p1ppp/pqp1p3/8/1b1P4/2NBPN2/PPPB1PPP/R2QK2R b KQkq -\"]\n[Timezone \"UTC\"]\n[ECO \"A40\"]\n[ECOUrl \"https://www.chess.com/openings/Queens-Pawn-Opening-Polish-Defense\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:43:00\"]\n[WhiteElo \"997\"]\n[BlackElo \"917\"]\n[TimeControl \"300\"]\n[Termination \"tigran-c-137 won - game abandoned\"]\n[StartTime \"08:43:00\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:45:21\"]\n[Link \"https://www.chess.com/game/live/53168947271\"]\n\n1. d4 {[%clk 0:04:48.8]} 1... b5 {[%clk 0:04:54.6]} 2. Nc3 {[%clk 0:04:40.2]} 2... Bb7 {[%clk 0:04:52.4]} 3. Nxb5 {[%clk 0:04:32.6]} 3... a6 {[%clk 0:04:51.5]} 4. Nc3 {[%clk 0:04:29.1]} 4... c6 {[%clk 0:04:49.9]} 5. e3 {[%clk 0:04:26.4]} 5... Qb6 {[%clk 0:04:37.4]} 6. Nf3 {[%clk 0:04:17.2]} 6... e6 {[%clk 0:04:35.9]} 7. Bd3 {[%clk 0:04:08.4]} 7... Bb4 {[%clk 0:04:35]} 8. Bd2 {[%clk 0:03:48.5]} 1-0\n",
		EndTimestamp: 1659429921,
	}

	existingGame2 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53168974013",
		Resource:     "https://www.chess.com/game/live/53168974013",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"NguoiandanhVN\"]\n[Result \"0-1\"]\n[CurrentPosition \"r3r1k1/2p1nppp/1p2p3/p7/P2P1N2/qP1QP3/5PPP/5RK1 w - -\"]\n[Timezone \"UTC\"]\n[ECO \"D31\"]\n[ECOUrl \"https://www.chess.com/openings/Queens-Gambit-Declined-Queens-Knight-Variation-3...dxc4\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:45:28\"]\n[WhiteElo \"988\"]\n[BlackElo \"983\"]\n[TimeControl \"300\"]\n[Termination \"NguoiandanhVN won by resignation\"]\n[StartTime \"08:45:28\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:49:00\"]\n[Link \"https://www.chess.com/game/live/53168974013\"]\n\n1. d4 {[%clk 0:05:00]} 1... d5 {[%clk 0:04:59.5]} 2. c4 {[%clk 0:04:58.9]} 2... e6 {[%clk 0:04:59.2]} 3. Nc3 {[%clk 0:04:56]} 3... dxc4 {[%clk 0:04:57.9]} 4. e3 {[%clk 0:04:54.8]} 4... Nf6 {[%clk 0:04:55.1]} 5. Bxc4 {[%clk 0:04:47.1]} 5... b6 {[%clk 0:04:53.8]} 6. Nf3 {[%clk 0:04:42.1]} 6... Be7 {[%clk 0:04:52.5]} 7. O-O {[%clk 0:04:34.2]} 7... O-O {[%clk 0:04:50.4]} 8. b3 {[%clk 0:04:22.8]} 8... Nc6 {[%clk 0:04:48.3]} 9. Nb1 {[%clk 0:04:17.6]} 9... a5 {[%clk 0:04:43.2]} 10. a4 {[%clk 0:04:06.8]} 10... Ba6 {[%clk 0:04:39.8]} 11. Nbd2 {[%clk 0:03:49.1]} 11... Bxc4 {[%clk 0:04:37.7]} 12. Nxc4 {[%clk 0:03:47.9]} 12... Nb4 {[%clk 0:04:36.3]} 13. Ba3 {[%clk 0:03:42.1]} 13... Ne4 {[%clk 0:04:32.3]} 14. Nfd2 {[%clk 0:03:33.6]} 14... Nxd2 {[%clk 0:04:30.6]} 15. Nxd2 {[%clk 0:03:27.5]} 15... Qd5 {[%clk 0:04:16.5]} 16. Nf3 {[%clk 0:03:11.3]} 16... Rfe8 {[%clk 0:04:14]} 17. Ne1 {[%clk 0:03:09.6]} 17... Nc6 {[%clk 0:04:06.9]} 18. Nd3 {[%clk 0:02:58.1]} 18... Bxa3 {[%clk 0:04:04.5]} 19. Rxa3 {[%clk 0:02:56.5]} 19... Ne7 {[%clk 0:03:59.6]} 20. Nf4 {[%clk 0:02:54.6]} 20... Qd6 {[%clk 0:03:54]} 21. Qd3 {[%clk 0:02:51.7]} 21... Qxa3 {[%clk 0:03:52.2]} 0-1\n",
		EndTimestamp: 1659430140,
	}

	existingGame3 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53169013011",
		Resource:     "https://www.chess.com/game/live/53169013011",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"VincentVeganin\"]\n[Result \"1-0\"]\n[CurrentPosition \"5Q2/7p/6pk/1R6/p7/2P1P2P/2q2PP1/6K1 b - -\"]\n[Timezone \"UTC\"]\n[ECO \"A40\"]\n[ECOUrl \"https://www.chess.com/openings/Modern-Defense-with-1-d4-2.Nc3-Bg7\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:49:01\"]\n[WhiteElo \"997\"]\n[BlackElo \"1027\"]\n[TimeControl \"300\"]\n[Termination \"tigran-c-137 won by checkmate\"]\n[StartTime \"08:49:01\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:57:23\"]\n[Link \"https://www.chess.com/game/live/53169013011\"]\n\n1. d4 {[%clk 0:05:00]} 1... g6 {[%clk 0:04:59.9]} 2. Nc3 {[%clk 0:04:57.9]} 2... Bg7 {[%clk 0:04:59]} 3. Nf3 {[%clk 0:04:55.2]} 3... Nc6 {[%clk 0:04:57]} 4. e3 {[%clk 0:04:51.4]} 4... d6 {[%clk 0:04:51.6]} 5. Bb5 {[%clk 0:04:49.2]} 5... Bd7 {[%clk 0:04:46.2]} 6. O-O {[%clk 0:04:47.5]} 6... a6 {[%clk 0:04:45.4]} 7. Bxc6 {[%clk 0:04:29.2]} 7... Bxc6 {[%clk 0:04:45.3]} 8. d5 {[%clk 0:04:28.4]} 8... Bxc3 {[%clk 0:04:23.5]} 9. dxc6 {[%clk 0:04:17.4]} 9... Bxb2 {[%clk 0:04:21.5]} 10. Bxb2 {[%clk 0:03:55.5]} 10... Nf6 {[%clk 0:04:17.8]} 11. Bxf6 {[%clk 0:03:51.1]} 11... exf6 {[%clk 0:04:15.3]} 12. cxb7 {[%clk 0:03:50.2]} 12... Rb8 {[%clk 0:04:14.7]} 13. Rb1 {[%clk 0:03:49.4]} 13... O-O {[%clk 0:04:12]} 14. Rb3 {[%clk 0:03:38.7]} 14... Qd7 {[%clk 0:03:52.4]} 15. Qd4 {[%clk 0:03:27.9]} 15... c5 {[%clk 0:03:43.8]} 16. Qxf6 {[%clk 0:03:22.2]} 16... Rxb7 {[%clk 0:03:34.1]} 17. Rxb7 {[%clk 0:03:10.1]} 17... Qxb7 {[%clk 0:03:34]} 18. Qxd6 {[%clk 0:03:08.9]} 18... Rb8 {[%clk 0:03:19.7]} 19. h3 {[%clk 0:03:03.5]} 19... c4 {[%clk 0:03:15.8]} 20. c3 {[%clk 0:02:24.2]} 20... Qb2 {[%clk 0:03:00.8]} 21. Qd2 {[%clk 0:02:19]} 21... Qb6 {[%clk 0:02:38.9]} 22. Qd4 {[%clk 0:02:12.9]} 22... Qb2 {[%clk 0:02:28.9]} 23. Qxc4 {[%clk 0:01:58]} 23... a5 {[%clk 0:02:24.1]} 24. Ne5 {[%clk 0:01:46.6]} 24... Qc2 {[%clk 0:02:12.4]} 25. Qxf7+ {[%clk 0:01:44]} 25... Kh8 {[%clk 0:02:07.1]} 26. Qf6+ {[%clk 0:01:18]} 26... Kg8 {[%clk 0:02:03.9]} 27. Qe6+ {[%clk 0:01:16.5]} 27... Kh8 {[%clk 0:02:01.5]} 28. Nf7+ {[%clk 0:01:13.4]} 28... Kg8 {[%clk 0:01:58]} 29. Nh6+ {[%clk 0:00:59.3]} 29... Kg7 {[%clk 0:01:36.7]} 30. Qe5+ {[%clk 0:00:51.4]} 30... Kxh6 {[%clk 0:01:31.3]} 31. Qxb8 {[%clk 0:00:50.1]} 31... Qxa2 {[%clk 0:01:28.4]} 32. Rb1 {[%clk 0:00:43.8]} 32... a4 {[%clk 0:01:25.1]} 33. Rb5 {[%clk 0:00:41.5]} 33... Qc2 {[%clk 0:01:18]} 34. Qf8# {[%clk 0:00:37.1]} 1-0\n",
		EndTimestamp: 1659430643,
	}

	newGame1 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53169604577",
		Resource:     "https://www.chess.com/game/live/53169604577",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"N-60\"]\n[Black \"tigran-c-137\"]\n[Result \"1-0\"]\n[CurrentPosition \"5Q1k/pbp3pp/1p6/4P1q1/2B5/8/PPP3PP/R6K b - -\"]\n[Timezone \"UTC\"]\n[ECO \"C34\"]\n[ECOUrl \"https://www.chess.com/openings/Kings-Gambit-Accepted-Schallopp-Defense-4.e5\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:58:14\"]\n[WhiteElo \"1067\"]\n[BlackElo \"990\"]\n[TimeControl \"300\"]\n[Termination \"N-60 won by checkmate\"]\n[StartTime \"08:58:14\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"09:04:04\"]\n[Link \"https://www.chess.com/game/live/53169604577\"]\n\n1. e4 {[%clk 0:04:57.7]} 1... e5 {[%clk 0:04:58.9]} 2. f4 {[%clk 0:04:54.8]} 2... exf4 {[%clk 0:04:57.2]} 3. Nf3 {[%clk 0:04:53.7]} 3... Nf6 {[%clk 0:04:50.9]} 4. e5 {[%clk 0:04:50.6]} 4... Ng4 {[%clk 0:04:07.6]} 5. d4 {[%clk 0:04:46]} 5... Be7 {[%clk 0:03:50.5]} 6. Bc4 {[%clk 0:04:36.6]} 6... O-O {[%clk 0:03:44.1]} 7. Bxf4 {[%clk 0:04:33.5]} 7... d5 {[%clk 0:03:31.9]} 8. Bd3 {[%clk 0:04:29.3]} 8... f6 {[%clk 0:03:25.3]} 9. O-O {[%clk 0:04:23.3]} 9... fxe5 {[%clk 0:03:20]} 10. Bxe5 {[%clk 0:04:21.6]} 10... Nxe5 {[%clk 0:03:11.7]} 11. Nxe5 {[%clk 0:04:19.3]} 11... Nd7 {[%clk 0:02:53]} 12. Nc3 {[%clk 0:04:10.5]} 12... Nxe5 {[%clk 0:02:50.4]} 13. dxe5 {[%clk 0:04:08.7]} 13... Bc5+ {[%clk 0:02:04.9]} 14. Kh1 {[%clk 0:04:05.3]} 14... b6 {[%clk 0:01:58]} 15. Nxd5 {[%clk 0:04:02.9]} 15... Bb7 {[%clk 0:01:44.7]} 16. Nf4 {[%clk 0:03:51.8]} 16... Rxf4 {[%clk 0:01:19.7]} 17. Rxf4 {[%clk 0:03:46.7]} 17... Qg5 {[%clk 0:01:18.7]} 18. Qf1 {[%clk 0:03:30.9]} 18... Rf8 {[%clk 0:01:06.8]} 19. Rxf8+ {[%clk 0:03:25.7]} 19... Bxf8 {[%clk 0:01:04.9]} 20. Bc4+ {[%clk 0:03:21]} 20... Kh8 {[%clk 0:01:02.4]} 21. Qxf8# {[%clk 0:03:18.1]} 1-0\n",
		EndTimestamp: 1659431044,
	}

	newGame2 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53170160741",
		Resource:     "https://www.chess.com/game/live/53170160741",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"yayancito24\"]\n[Black \"tigran-c-137\"]\n[Result \"1-0\"]\n[CurrentPosition \"6k1/pp4p1/7p/3n4/2BN4/8/PP1b1PPP/5K2 b - -\"]\n[Timezone \"UTC\"]\n[ECO \"C20\"]\n[ECOUrl \"https://www.chess.com/openings/Kings-Pawn-Opening-Wayward-Queen-Kiddie-Countergambit\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"09:04:12\"]\n[WhiteElo \"898\"]\n[BlackElo \"980\"]\n[TimeControl \"300\"]\n[Termination \"yayancito24 won by resignation\"]\n[StartTime \"09:04:12\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"09:09:02\"]\n[Link \"https://www.chess.com/game/live/53170160741\"]\n\n1. e4 {[%clk 0:05:00]} 1... e5 {[%clk 0:04:59.9]} 2. Qh5 {[%clk 0:04:59.2]} 2... Nf6 {[%clk 0:04:54.3]} 3. Qxe5+ {[%clk 0:04:57.4]} 3... Qe7 {[%clk 0:04:47.1]} 4. Qxe7+ {[%clk 0:04:56.4]} 4... Bxe7 {[%clk 0:04:45.4]} 5. d3 {[%clk 0:04:56.3]} 5... O-O {[%clk 0:04:43]} 6. Bg5 {[%clk 0:04:55.6]} 6... h6 {[%clk 0:04:39.1]} 7. Bxf6 {[%clk 0:04:54.6]} 7... Bxf6 {[%clk 0:04:38.1]} 8. c3 {[%clk 0:04:53.7]} 8... c6 {[%clk 0:04:32.5]} 9. Nf3 {[%clk 0:04:53]} 9... d5 {[%clk 0:04:30.8]} 10. e5 {[%clk 0:04:48.2]} 10... Re8 {[%clk 0:04:25.5]} 11. d4 {[%clk 0:04:47.1]} 11... Bg4 {[%clk 0:04:10.1]} 12. Be2 {[%clk 0:04:45.3]} 12... Bxf3 {[%clk 0:04:08.3]} 13. Bxf3 {[%clk 0:04:44.3]} 13... Bg5 {[%clk 0:03:37.6]} 14. Na3 {[%clk 0:04:39]} 14... f6 {[%clk 0:03:36.5]} 15. O-O {[%clk 0:04:31.9]} 15... fxe5 {[%clk 0:03:33.8]} 16. dxe5 {[%clk 0:04:30.9]} 16... Rxe5 {[%clk 0:03:30.4]} 17. Rfe1 {[%clk 0:04:30.8]} 17... Rxe1+ {[%clk 0:03:17.8]} 18. Rxe1 {[%clk 0:04:30.7]} 18... Nd7 {[%clk 0:03:12.4]} 19. Nc2 {[%clk 0:04:21.9]} 19... Nb6 {[%clk 0:02:35.5]} 20. Nd4 {[%clk 0:04:19.6]} 20... c5 {[%clk 0:02:28.5]} 21. Ne6 {[%clk 0:04:17.8]} 21... Bd2 {[%clk 0:02:24.9]} 22. Re2 {[%clk 0:04:13.9]} 22... Re8 {[%clk 0:02:21]} 23. Kf1 {[%clk 0:04:11.5]} 23... d4 {[%clk 0:01:58.2]} 24. cxd4 {[%clk 0:04:04.2]} 24... cxd4 {[%clk 0:01:54.3]} 25. Nxd4 {[%clk 0:04:03]} 25... Rxe2 {[%clk 0:01:46.3]} 26. Bxe2 {[%clk 0:03:59.8]} 26... Nd5 {[%clk 0:01:41.2]} 27. Bc4 {[%clk 0:03:56.5]} 1-0\n",
		EndTimestamp: 1659431342,
	}

	newGame3 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53170213967",
		Resource:     "https://www.chess.com/game/live/53170213967",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"philoz87\"]\n[Black \"tigran-c-137\"]\n[Result \"0-1\"]\n[CurrentPosition \"r2q1rk1/ppp2pp1/2n4B/3pP3/2B3b1/8/PPP2PPP/RN3RK1 w - -\"]\n[Timezone \"UTC\"]\n[ECO \"C42\"]\n[ECOUrl \"https://www.chess.com/openings/Petrovs-Defense-Urusov-Gambit\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"09:09:05\"]\n[WhiteElo \"999\"]\n[BlackElo \"989\"]\n[TimeControl \"300\"]\n[Termination \"tigran-c-137 won by resignation\"]\n[StartTime \"09:09:05\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"09:10:45\"]\n[Link \"https://www.chess.com/game/live/53170213967\"]\n\n1. e4 {[%clk 0:05:00]} 1... e5 {[%clk 0:04:59.9]} 2. Bc4 {[%clk 0:04:58.1]} 2... Nf6 {[%clk 0:04:55.5]} 3. Nf3 {[%clk 0:04:56.6]} 3... h6 {[%clk 0:04:54.4]} 4. d4 {[%clk 0:04:55.5]} 4... exd4 {[%clk 0:04:49.1]} 5. Nxd4 {[%clk 0:04:52.6]} 5... Bc5 {[%clk 0:04:46.4]} 6. O-O {[%clk 0:04:45.6]} 6... O-O {[%clk 0:04:44.2]} 7. e5 {[%clk 0:04:42.1]} 7... Bxd4 {[%clk 0:04:38.7]} 8. Qxd4 {[%clk 0:04:40.4]} 8... Nc6 {[%clk 0:04:37.6]} 9. Qf4 {[%clk 0:04:30.6]} 9... Ng4 {[%clk 0:04:13.3]} 10. Qxg4 {[%clk 0:04:27.5]} 10... d5 {[%clk 0:04:12.4]} 11. Bxh6 {[%clk 0:04:20.4]} 11... Bxg4 {[%clk 0:04:08.5]} 0-1\n",
		EndTimestamp: 1659431445,
	}

	err = gamesTable.PutGameRecords([]games.GameRecord{existingGame1, existingGame2, existingGame3})
	assert.NoError(t, err)

	downloadId := uuid.New().String()
	downloadRecord := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    3,
		Failed:     0,
		Done:       3,
		Pending:    2,
		Total:      5,
	}

	err = downloadsTable.PutDownloadRecord(downloadRecord)
	assert.NoError(t, err)

	stubDownload, err := downloader.stubChessDotCom(username, "2022", "08")
	assert.NoError(t, err)

	err = wiremockClient.StubFor(stubDownload)
	assert.NoError(t, err)

	command :=
		events.SQSMessage{
			Body: fmt.Sprintf(
				`
				{
					"username": "%s",
					"userId": "%s",
					"platform": "CHESS_DOT_COM",
					"archiveId": "%s",
					"downloadId": "%s",
					"hehe": "💣"
				}
			`,
				username,
				userId,
				archiveId,
				downloadId,
			),
			MessageId: "1",
		}

	actualCommandsProcessed, err := downloader.Download(events.SQSEvent{Records: []events.SQSMessage{command}})
	assert.NoError(t, err)
	expectedCommandsProcessed := events.SQSEventResponse{
		BatchItemFailures: nil,
	}
	assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

	actualArchive, err := archivesTable.GetArchiveRecord(userId, archiveId)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive)

	assert.Equal(t, 6, actualArchive.Downloaded)

	startOfChecking := time.Now().UTC()

	assert.True(t, startOfChecking.After(actualArchive.DownloadedAt.ToTime()))
	assert.True(t, startOfTest.Before(actualArchive.DownloadedAt.ToTime()))

	var noKey map[string]*dynamodb.AttributeValue
	actualGames, _, err := gamesTable.QueryGames(userId, noKey, 1000)
	assert.NoError(t, err)

	expectedGames := []games.GameRecord{
		existingGame1,
		existingGame2,
		existingGame3,
		newGame1,
		newGame2,
		newGame3,
	}

	assert.Equal(t, 6, len(actualGames))
	assert.ElementsMatch(t, expectedGames, actualGames)

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownload)

	expectedDownload := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    4,
		Failed:     0,
		Done:       4,
		Pending:    1,
		Total:      5,
	}

	assert.Equal(t, expectedDownload, *actualDownload)

	verifyDownloadedCall, err := wiremockClient.Verify(stubDownload.Request(), 1)
	assert.NoError(t, err)
	assert.True(t, verifyDownloadedCall)
}

func Test_when_archive_is_reset_yet_partially_downloaded_CommitDownloader_should_download_all_games_even_those_that_have_been_downloaded(t *testing.T) {
	defer wiremockClient.Reset()
	downloader.pgnFilter = IdentityPgnFilter{}

	startOfTest := time.Now().UTC()

	var err error
	username := uuid.New().String()
	userId := uuid.New().String()
	archiveId := uuid.New().String()

	archiveRecord := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		Resource:     archiveId,
		Year:         2022,
		Month:        8,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	err = archivesTable.PutArchiveRecord(archiveRecord)
	assert.NoError(t, err)

	existingGame1 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53168947271",
		Resource:     "https://www.chess.com/very/very/very/old/game/53168947271",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"akashvishwakarma000\"]\n[Result \"1-0\"]\n[CurrentPosition \"rn2k1nr/1b1p1ppp/pqp1p3/8/1b1P4/2NBPN2/PPPB1PPP/R2QK2R b KQkq -\"]\n[Timezone \"UTC\"]\n[ECO \"A40\"]\n[ECOUrl \"https://www.chess.com/openings/Queens-Pawn-Opening-Polish-Defense\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:43:00\"]\n[WhiteElo \"997\"]\n[BlackElo \"917\"]\n[TimeControl \"300\"]\n[Termination \"tigran-c-137 won - game abandoned\"]\n[StartTime \"08:43:00\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:45:21\"]\n[Link \"https://www.chess.com/game/live/53168947271\"]\n\n1. d4 {[%clk 0:04:48.8]} 1... b5 {[%clk 0:04:54.6]} 2. Nc3 {[%clk 0:04:40.2]} 2... Bb7 {[%clk 0:04:52.4]} 3. Nxb5 {[%clk 0:04:32.6]} 3... a6 {[%clk 0:04:51.5]} 4. Nc3 {[%clk 0:04:29.1]} 4... c6 {[%clk 0:04:49.9]} 5. e3 {[%clk 0:04:26.4]} 5... Qb6 {[%clk 0:04:37.4]} 6. Nf3 {[%clk 0:04:17.2]} 6... e6 {[%clk 0:04:35.9]} 7. Bd3 {[%clk 0:04:08.4]} 7... Bb4 {[%clk 0:04:35]} 8. Bd2 {[%clk 0:03:48.5]} 1-0\n",
		EndTimestamp: 1659429921,
	}

	existingGame2 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53168974013",
		Resource:     "https://www.chess.com/very/very/very/old/game/live/53168974013",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"NguoiandanhVN\"]\n[Result \"0-1\"]\n[CurrentPosition \"r3r1k1/2p1nppp/1p2p3/p7/P2P1N2/qP1QP3/5PPP/5RK1 w - -\"]\n[Timezone \"UTC\"]\n[ECO \"D31\"]\n[ECOUrl \"https://www.chess.com/openings/Queens-Gambit-Declined-Queens-Knight-Variation-3...dxc4\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:45:28\"]\n[WhiteElo \"988\"]\n[BlackElo \"983\"]\n[TimeControl \"300\"]\n[Termination \"NguoiandanhVN won by resignation\"]\n[StartTime \"08:45:28\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:49:00\"]\n[Link \"https://www.chess.com/game/live/53168974013\"]\n\n1. d4 {[%clk 0:05:00]} 1... d5 {[%clk 0:04:59.5]} 2. c4 {[%clk 0:04:58.9]} 2... e6 {[%clk 0:04:59.2]} 3. Nc3 {[%clk 0:04:56]} 3... dxc4 {[%clk 0:04:57.9]} 4. e3 {[%clk 0:04:54.8]} 4... Nf6 {[%clk 0:04:55.1]} 5. Bxc4 {[%clk 0:04:47.1]} 5... b6 {[%clk 0:04:53.8]} 6. Nf3 {[%clk 0:04:42.1]} 6... Be7 {[%clk 0:04:52.5]} 7. O-O {[%clk 0:04:34.2]} 7... O-O {[%clk 0:04:50.4]} 8. b3 {[%clk 0:04:22.8]} 8... Nc6 {[%clk 0:04:48.3]} 9. Nb1 {[%clk 0:04:17.6]} 9... a5 {[%clk 0:04:43.2]} 10. a4 {[%clk 0:04:06.8]} 10... Ba6 {[%clk 0:04:39.8]} 11. Nbd2 {[%clk 0:03:49.1]} 11... Bxc4 {[%clk 0:04:37.7]} 12. Nxc4 {[%clk 0:03:47.9]} 12... Nb4 {[%clk 0:04:36.3]} 13. Ba3 {[%clk 0:03:42.1]} 13... Ne4 {[%clk 0:04:32.3]} 14. Nfd2 {[%clk 0:03:33.6]} 14... Nxd2 {[%clk 0:04:30.6]} 15. Nxd2 {[%clk 0:03:27.5]} 15... Qd5 {[%clk 0:04:16.5]} 16. Nf3 {[%clk 0:03:11.3]} 16... Rfe8 {[%clk 0:04:14]} 17. Ne1 {[%clk 0:03:09.6]} 17... Nc6 {[%clk 0:04:06.9]} 18. Nd3 {[%clk 0:02:58.1]} 18... Bxa3 {[%clk 0:04:04.5]} 19. Rxa3 {[%clk 0:02:56.5]} 19... Ne7 {[%clk 0:03:59.6]} 20. Nf4 {[%clk 0:02:54.6]} 20... Qd6 {[%clk 0:03:54]} 21. Qd3 {[%clk 0:02:51.7]} 21... Qxa3 {[%clk 0:03:52.2]} 0-1\n",
		EndTimestamp: 1659430140,
	}

	existingGame3 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53169013011",
		Resource:     "https://www.chess.com/very/very/very/old/game/live/53169013011",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"VincentVeganin\"]\n[Result \"1-0\"]\n[CurrentPosition \"5Q2/7p/6pk/1R6/p7/2P1P2P/2q2PP1/6K1 b - -\"]\n[Timezone \"UTC\"]\n[ECO \"A40\"]\n[ECOUrl \"https://www.chess.com/openings/Modern-Defense-with-1-d4-2.Nc3-Bg7\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:49:01\"]\n[WhiteElo \"997\"]\n[BlackElo \"1027\"]\n[TimeControl \"300\"]\n[Termination \"tigran-c-137 won by checkmate\"]\n[StartTime \"08:49:01\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:57:23\"]\n[Link \"https://www.chess.com/game/live/53169013011\"]\n\n1. d4 {[%clk 0:05:00]} 1... g6 {[%clk 0:04:59.9]} 2. Nc3 {[%clk 0:04:57.9]} 2... Bg7 {[%clk 0:04:59]} 3. Nf3 {[%clk 0:04:55.2]} 3... Nc6 {[%clk 0:04:57]} 4. e3 {[%clk 0:04:51.4]} 4... d6 {[%clk 0:04:51.6]} 5. Bb5 {[%clk 0:04:49.2]} 5... Bd7 {[%clk 0:04:46.2]} 6. O-O {[%clk 0:04:47.5]} 6... a6 {[%clk 0:04:45.4]} 7. Bxc6 {[%clk 0:04:29.2]} 7... Bxc6 {[%clk 0:04:45.3]} 8. d5 {[%clk 0:04:28.4]} 8... Bxc3 {[%clk 0:04:23.5]} 9. dxc6 {[%clk 0:04:17.4]} 9... Bxb2 {[%clk 0:04:21.5]} 10. Bxb2 {[%clk 0:03:55.5]} 10... Nf6 {[%clk 0:04:17.8]} 11. Bxf6 {[%clk 0:03:51.1]} 11... exf6 {[%clk 0:04:15.3]} 12. cxb7 {[%clk 0:03:50.2]} 12... Rb8 {[%clk 0:04:14.7]} 13. Rb1 {[%clk 0:03:49.4]} 13... O-O {[%clk 0:04:12]} 14. Rb3 {[%clk 0:03:38.7]} 14... Qd7 {[%clk 0:03:52.4]} 15. Qd4 {[%clk 0:03:27.9]} 15... c5 {[%clk 0:03:43.8]} 16. Qxf6 {[%clk 0:03:22.2]} 16... Rxb7 {[%clk 0:03:34.1]} 17. Rxb7 {[%clk 0:03:10.1]} 17... Qxb7 {[%clk 0:03:34]} 18. Qxd6 {[%clk 0:03:08.9]} 18... Rb8 {[%clk 0:03:19.7]} 19. h3 {[%clk 0:03:03.5]} 19... c4 {[%clk 0:03:15.8]} 20. c3 {[%clk 0:02:24.2]} 20... Qb2 {[%clk 0:03:00.8]} 21. Qd2 {[%clk 0:02:19]} 21... Qb6 {[%clk 0:02:38.9]} 22. Qd4 {[%clk 0:02:12.9]} 22... Qb2 {[%clk 0:02:28.9]} 23. Qxc4 {[%clk 0:01:58]} 23... a5 {[%clk 0:02:24.1]} 24. Ne5 {[%clk 0:01:46.6]} 24... Qc2 {[%clk 0:02:12.4]} 25. Qxf7+ {[%clk 0:01:44]} 25... Kh8 {[%clk 0:02:07.1]} 26. Qf6+ {[%clk 0:01:18]} 26... Kg8 {[%clk 0:02:03.9]} 27. Qe6+ {[%clk 0:01:16.5]} 27... Kh8 {[%clk 0:02:01.5]} 28. Nf7+ {[%clk 0:01:13.4]} 28... Kg8 {[%clk 0:01:58]} 29. Nh6+ {[%clk 0:00:59.3]} 29... Kg7 {[%clk 0:01:36.7]} 30. Qe5+ {[%clk 0:00:51.4]} 30... Kxh6 {[%clk 0:01:31.3]} 31. Qxb8 {[%clk 0:00:50.1]} 31... Qxa2 {[%clk 0:01:28.4]} 32. Rb1 {[%clk 0:00:43.8]} 32... a4 {[%clk 0:01:25.1]} 33. Rb5 {[%clk 0:00:41.5]} 33... Qc2 {[%clk 0:01:18]} 34. Qf8# {[%clk 0:00:37.1]} 1-0\n",
		EndTimestamp: 1659430643,
	}

	existingGame1Redownloaded := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53168947271",
		Resource:     "https://www.chess.com/game/live/53168947271",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"akashvishwakarma000\"]\n[Result \"1-0\"]\n[CurrentPosition \"rn2k1nr/1b1p1ppp/pqp1p3/8/1b1P4/2NBPN2/PPPB1PPP/R2QK2R b KQkq -\"]\n[Timezone \"UTC\"]\n[ECO \"A40\"]\n[ECOUrl \"https://www.chess.com/openings/Queens-Pawn-Opening-Polish-Defense\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:43:00\"]\n[WhiteElo \"997\"]\n[BlackElo \"917\"]\n[TimeControl \"300\"]\n[Termination \"tigran-c-137 won - game abandoned\"]\n[StartTime \"08:43:00\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:45:21\"]\n[Link \"https://www.chess.com/game/live/53168947271\"]\n\n1. d4 {[%clk 0:04:48.8]} 1... b5 {[%clk 0:04:54.6]} 2. Nc3 {[%clk 0:04:40.2]} 2... Bb7 {[%clk 0:04:52.4]} 3. Nxb5 {[%clk 0:04:32.6]} 3... a6 {[%clk 0:04:51.5]} 4. Nc3 {[%clk 0:04:29.1]} 4... c6 {[%clk 0:04:49.9]} 5. e3 {[%clk 0:04:26.4]} 5... Qb6 {[%clk 0:04:37.4]} 6. Nf3 {[%clk 0:04:17.2]} 6... e6 {[%clk 0:04:35.9]} 7. Bd3 {[%clk 0:04:08.4]} 7... Bb4 {[%clk 0:04:35]} 8. Bd2 {[%clk 0:03:48.5]} 1-0\n",
		EndTimestamp: 1659429921,
	}

	existingGame2Redownloaded := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53168974013",
		Resource:     "https://www.chess.com/game/live/53168974013",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"NguoiandanhVN\"]\n[Result \"0-1\"]\n[CurrentPosition \"r3r1k1/2p1nppp/1p2p3/p7/P2P1N2/qP1QP3/5PPP/5RK1 w - -\"]\n[Timezone \"UTC\"]\n[ECO \"D31\"]\n[ECOUrl \"https://www.chess.com/openings/Queens-Gambit-Declined-Queens-Knight-Variation-3...dxc4\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:45:28\"]\n[WhiteElo \"988\"]\n[BlackElo \"983\"]\n[TimeControl \"300\"]\n[Termination \"NguoiandanhVN won by resignation\"]\n[StartTime \"08:45:28\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:49:00\"]\n[Link \"https://www.chess.com/game/live/53168974013\"]\n\n1. d4 {[%clk 0:05:00]} 1... d5 {[%clk 0:04:59.5]} 2. c4 {[%clk 0:04:58.9]} 2... e6 {[%clk 0:04:59.2]} 3. Nc3 {[%clk 0:04:56]} 3... dxc4 {[%clk 0:04:57.9]} 4. e3 {[%clk 0:04:54.8]} 4... Nf6 {[%clk 0:04:55.1]} 5. Bxc4 {[%clk 0:04:47.1]} 5... b6 {[%clk 0:04:53.8]} 6. Nf3 {[%clk 0:04:42.1]} 6... Be7 {[%clk 0:04:52.5]} 7. O-O {[%clk 0:04:34.2]} 7... O-O {[%clk 0:04:50.4]} 8. b3 {[%clk 0:04:22.8]} 8... Nc6 {[%clk 0:04:48.3]} 9. Nb1 {[%clk 0:04:17.6]} 9... a5 {[%clk 0:04:43.2]} 10. a4 {[%clk 0:04:06.8]} 10... Ba6 {[%clk 0:04:39.8]} 11. Nbd2 {[%clk 0:03:49.1]} 11... Bxc4 {[%clk 0:04:37.7]} 12. Nxc4 {[%clk 0:03:47.9]} 12... Nb4 {[%clk 0:04:36.3]} 13. Ba3 {[%clk 0:03:42.1]} 13... Ne4 {[%clk 0:04:32.3]} 14. Nfd2 {[%clk 0:03:33.6]} 14... Nxd2 {[%clk 0:04:30.6]} 15. Nxd2 {[%clk 0:03:27.5]} 15... Qd5 {[%clk 0:04:16.5]} 16. Nf3 {[%clk 0:03:11.3]} 16... Rfe8 {[%clk 0:04:14]} 17. Ne1 {[%clk 0:03:09.6]} 17... Nc6 {[%clk 0:04:06.9]} 18. Nd3 {[%clk 0:02:58.1]} 18... Bxa3 {[%clk 0:04:04.5]} 19. Rxa3 {[%clk 0:02:56.5]} 19... Ne7 {[%clk 0:03:59.6]} 20. Nf4 {[%clk 0:02:54.6]} 20... Qd6 {[%clk 0:03:54]} 21. Qd3 {[%clk 0:02:51.7]} 21... Qxa3 {[%clk 0:03:52.2]} 0-1\n",
		EndTimestamp: 1659430140,
	}

	existingGame3Redownloaded := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53169013011",
		Resource:     "https://www.chess.com/game/live/53169013011",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"VincentVeganin\"]\n[Result \"1-0\"]\n[CurrentPosition \"5Q2/7p/6pk/1R6/p7/2P1P2P/2q2PP1/6K1 b - -\"]\n[Timezone \"UTC\"]\n[ECO \"A40\"]\n[ECOUrl \"https://www.chess.com/openings/Modern-Defense-with-1-d4-2.Nc3-Bg7\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:49:01\"]\n[WhiteElo \"997\"]\n[BlackElo \"1027\"]\n[TimeControl \"300\"]\n[Termination \"tigran-c-137 won by checkmate\"]\n[StartTime \"08:49:01\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:57:23\"]\n[Link \"https://www.chess.com/game/live/53169013011\"]\n\n1. d4 {[%clk 0:05:00]} 1... g6 {[%clk 0:04:59.9]} 2. Nc3 {[%clk 0:04:57.9]} 2... Bg7 {[%clk 0:04:59]} 3. Nf3 {[%clk 0:04:55.2]} 3... Nc6 {[%clk 0:04:57]} 4. e3 {[%clk 0:04:51.4]} 4... d6 {[%clk 0:04:51.6]} 5. Bb5 {[%clk 0:04:49.2]} 5... Bd7 {[%clk 0:04:46.2]} 6. O-O {[%clk 0:04:47.5]} 6... a6 {[%clk 0:04:45.4]} 7. Bxc6 {[%clk 0:04:29.2]} 7... Bxc6 {[%clk 0:04:45.3]} 8. d5 {[%clk 0:04:28.4]} 8... Bxc3 {[%clk 0:04:23.5]} 9. dxc6 {[%clk 0:04:17.4]} 9... Bxb2 {[%clk 0:04:21.5]} 10. Bxb2 {[%clk 0:03:55.5]} 10... Nf6 {[%clk 0:04:17.8]} 11. Bxf6 {[%clk 0:03:51.1]} 11... exf6 {[%clk 0:04:15.3]} 12. cxb7 {[%clk 0:03:50.2]} 12... Rb8 {[%clk 0:04:14.7]} 13. Rb1 {[%clk 0:03:49.4]} 13... O-O {[%clk 0:04:12]} 14. Rb3 {[%clk 0:03:38.7]} 14... Qd7 {[%clk 0:03:52.4]} 15. Qd4 {[%clk 0:03:27.9]} 15... c5 {[%clk 0:03:43.8]} 16. Qxf6 {[%clk 0:03:22.2]} 16... Rxb7 {[%clk 0:03:34.1]} 17. Rxb7 {[%clk 0:03:10.1]} 17... Qxb7 {[%clk 0:03:34]} 18. Qxd6 {[%clk 0:03:08.9]} 18... Rb8 {[%clk 0:03:19.7]} 19. h3 {[%clk 0:03:03.5]} 19... c4 {[%clk 0:03:15.8]} 20. c3 {[%clk 0:02:24.2]} 20... Qb2 {[%clk 0:03:00.8]} 21. Qd2 {[%clk 0:02:19]} 21... Qb6 {[%clk 0:02:38.9]} 22. Qd4 {[%clk 0:02:12.9]} 22... Qb2 {[%clk 0:02:28.9]} 23. Qxc4 {[%clk 0:01:58]} 23... a5 {[%clk 0:02:24.1]} 24. Ne5 {[%clk 0:01:46.6]} 24... Qc2 {[%clk 0:02:12.4]} 25. Qxf7+ {[%clk 0:01:44]} 25... Kh8 {[%clk 0:02:07.1]} 26. Qf6+ {[%clk 0:01:18]} 26... Kg8 {[%clk 0:02:03.9]} 27. Qe6+ {[%clk 0:01:16.5]} 27... Kh8 {[%clk 0:02:01.5]} 28. Nf7+ {[%clk 0:01:13.4]} 28... Kg8 {[%clk 0:01:58]} 29. Nh6+ {[%clk 0:00:59.3]} 29... Kg7 {[%clk 0:01:36.7]} 30. Qe5+ {[%clk 0:00:51.4]} 30... Kxh6 {[%clk 0:01:31.3]} 31. Qxb8 {[%clk 0:00:50.1]} 31... Qxa2 {[%clk 0:01:28.4]} 32. Rb1 {[%clk 0:00:43.8]} 32... a4 {[%clk 0:01:25.1]} 33. Rb5 {[%clk 0:00:41.5]} 33... Qc2 {[%clk 0:01:18]} 34. Qf8# {[%clk 0:00:37.1]} 1-0\n",
		EndTimestamp: 1659430643,
	}

	newGame1 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53169604577",
		Resource:     "https://www.chess.com/game/live/53169604577",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"N-60\"]\n[Black \"tigran-c-137\"]\n[Result \"1-0\"]\n[CurrentPosition \"5Q1k/pbp3pp/1p6/4P1q1/2B5/8/PPP3PP/R6K b - -\"]\n[Timezone \"UTC\"]\n[ECO \"C34\"]\n[ECOUrl \"https://www.chess.com/openings/Kings-Gambit-Accepted-Schallopp-Defense-4.e5\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:58:14\"]\n[WhiteElo \"1067\"]\n[BlackElo \"990\"]\n[TimeControl \"300\"]\n[Termination \"N-60 won by checkmate\"]\n[StartTime \"08:58:14\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"09:04:04\"]\n[Link \"https://www.chess.com/game/live/53169604577\"]\n\n1. e4 {[%clk 0:04:57.7]} 1... e5 {[%clk 0:04:58.9]} 2. f4 {[%clk 0:04:54.8]} 2... exf4 {[%clk 0:04:57.2]} 3. Nf3 {[%clk 0:04:53.7]} 3... Nf6 {[%clk 0:04:50.9]} 4. e5 {[%clk 0:04:50.6]} 4... Ng4 {[%clk 0:04:07.6]} 5. d4 {[%clk 0:04:46]} 5... Be7 {[%clk 0:03:50.5]} 6. Bc4 {[%clk 0:04:36.6]} 6... O-O {[%clk 0:03:44.1]} 7. Bxf4 {[%clk 0:04:33.5]} 7... d5 {[%clk 0:03:31.9]} 8. Bd3 {[%clk 0:04:29.3]} 8... f6 {[%clk 0:03:25.3]} 9. O-O {[%clk 0:04:23.3]} 9... fxe5 {[%clk 0:03:20]} 10. Bxe5 {[%clk 0:04:21.6]} 10... Nxe5 {[%clk 0:03:11.7]} 11. Nxe5 {[%clk 0:04:19.3]} 11... Nd7 {[%clk 0:02:53]} 12. Nc3 {[%clk 0:04:10.5]} 12... Nxe5 {[%clk 0:02:50.4]} 13. dxe5 {[%clk 0:04:08.7]} 13... Bc5+ {[%clk 0:02:04.9]} 14. Kh1 {[%clk 0:04:05.3]} 14... b6 {[%clk 0:01:58]} 15. Nxd5 {[%clk 0:04:02.9]} 15... Bb7 {[%clk 0:01:44.7]} 16. Nf4 {[%clk 0:03:51.8]} 16... Rxf4 {[%clk 0:01:19.7]} 17. Rxf4 {[%clk 0:03:46.7]} 17... Qg5 {[%clk 0:01:18.7]} 18. Qf1 {[%clk 0:03:30.9]} 18... Rf8 {[%clk 0:01:06.8]} 19. Rxf8+ {[%clk 0:03:25.7]} 19... Bxf8 {[%clk 0:01:04.9]} 20. Bc4+ {[%clk 0:03:21]} 20... Kh8 {[%clk 0:01:02.4]} 21. Qxf8# {[%clk 0:03:18.1]} 1-0\n",
		EndTimestamp: 1659431044,
	}

	newGame2 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53170160741",
		Resource:     "https://www.chess.com/game/live/53170160741",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"yayancito24\"]\n[Black \"tigran-c-137\"]\n[Result \"1-0\"]\n[CurrentPosition \"6k1/pp4p1/7p/3n4/2BN4/8/PP1b1PPP/5K2 b - -\"]\n[Timezone \"UTC\"]\n[ECO \"C20\"]\n[ECOUrl \"https://www.chess.com/openings/Kings-Pawn-Opening-Wayward-Queen-Kiddie-Countergambit\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"09:04:12\"]\n[WhiteElo \"898\"]\n[BlackElo \"980\"]\n[TimeControl \"300\"]\n[Termination \"yayancito24 won by resignation\"]\n[StartTime \"09:04:12\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"09:09:02\"]\n[Link \"https://www.chess.com/game/live/53170160741\"]\n\n1. e4 {[%clk 0:05:00]} 1... e5 {[%clk 0:04:59.9]} 2. Qh5 {[%clk 0:04:59.2]} 2... Nf6 {[%clk 0:04:54.3]} 3. Qxe5+ {[%clk 0:04:57.4]} 3... Qe7 {[%clk 0:04:47.1]} 4. Qxe7+ {[%clk 0:04:56.4]} 4... Bxe7 {[%clk 0:04:45.4]} 5. d3 {[%clk 0:04:56.3]} 5... O-O {[%clk 0:04:43]} 6. Bg5 {[%clk 0:04:55.6]} 6... h6 {[%clk 0:04:39.1]} 7. Bxf6 {[%clk 0:04:54.6]} 7... Bxf6 {[%clk 0:04:38.1]} 8. c3 {[%clk 0:04:53.7]} 8... c6 {[%clk 0:04:32.5]} 9. Nf3 {[%clk 0:04:53]} 9... d5 {[%clk 0:04:30.8]} 10. e5 {[%clk 0:04:48.2]} 10... Re8 {[%clk 0:04:25.5]} 11. d4 {[%clk 0:04:47.1]} 11... Bg4 {[%clk 0:04:10.1]} 12. Be2 {[%clk 0:04:45.3]} 12... Bxf3 {[%clk 0:04:08.3]} 13. Bxf3 {[%clk 0:04:44.3]} 13... Bg5 {[%clk 0:03:37.6]} 14. Na3 {[%clk 0:04:39]} 14... f6 {[%clk 0:03:36.5]} 15. O-O {[%clk 0:04:31.9]} 15... fxe5 {[%clk 0:03:33.8]} 16. dxe5 {[%clk 0:04:30.9]} 16... Rxe5 {[%clk 0:03:30.4]} 17. Rfe1 {[%clk 0:04:30.8]} 17... Rxe1+ {[%clk 0:03:17.8]} 18. Rxe1 {[%clk 0:04:30.7]} 18... Nd7 {[%clk 0:03:12.4]} 19. Nc2 {[%clk 0:04:21.9]} 19... Nb6 {[%clk 0:02:35.5]} 20. Nd4 {[%clk 0:04:19.6]} 20... c5 {[%clk 0:02:28.5]} 21. Ne6 {[%clk 0:04:17.8]} 21... Bd2 {[%clk 0:02:24.9]} 22. Re2 {[%clk 0:04:13.9]} 22... Re8 {[%clk 0:02:21]} 23. Kf1 {[%clk 0:04:11.5]} 23... d4 {[%clk 0:01:58.2]} 24. cxd4 {[%clk 0:04:04.2]} 24... cxd4 {[%clk 0:01:54.3]} 25. Nxd4 {[%clk 0:04:03]} 25... Rxe2 {[%clk 0:01:46.3]} 26. Bxe2 {[%clk 0:03:59.8]} 26... Nd5 {[%clk 0:01:41.2]} 27. Bc4 {[%clk 0:03:56.5]} 1-0\n",
		EndTimestamp: 1659431342,
	}

	newGame3 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53170213967",
		Resource:     "https://www.chess.com/game/live/53170213967",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"philoz87\"]\n[Black \"tigran-c-137\"]\n[Result \"0-1\"]\n[CurrentPosition \"r2q1rk1/ppp2pp1/2n4B/3pP3/2B3b1/8/PPP2PPP/RN3RK1 w - -\"]\n[Timezone \"UTC\"]\n[ECO \"C42\"]\n[ECOUrl \"https://www.chess.com/openings/Petrovs-Defense-Urusov-Gambit\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"09:09:05\"]\n[WhiteElo \"999\"]\n[BlackElo \"989\"]\n[TimeControl \"300\"]\n[Termination \"tigran-c-137 won by resignation\"]\n[StartTime \"09:09:05\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"09:10:45\"]\n[Link \"https://www.chess.com/game/live/53170213967\"]\n\n1. e4 {[%clk 0:05:00]} 1... e5 {[%clk 0:04:59.9]} 2. Bc4 {[%clk 0:04:58.1]} 2... Nf6 {[%clk 0:04:55.5]} 3. Nf3 {[%clk 0:04:56.6]} 3... h6 {[%clk 0:04:54.4]} 4. d4 {[%clk 0:04:55.5]} 4... exd4 {[%clk 0:04:49.1]} 5. Nxd4 {[%clk 0:04:52.6]} 5... Bc5 {[%clk 0:04:46.4]} 6. O-O {[%clk 0:04:45.6]} 6... O-O {[%clk 0:04:44.2]} 7. e5 {[%clk 0:04:42.1]} 7... Bxd4 {[%clk 0:04:38.7]} 8. Qxd4 {[%clk 0:04:40.4]} 8... Nc6 {[%clk 0:04:37.6]} 9. Qf4 {[%clk 0:04:30.6]} 9... Ng4 {[%clk 0:04:13.3]} 10. Qxg4 {[%clk 0:04:27.5]} 10... d5 {[%clk 0:04:12.4]} 11. Bxh6 {[%clk 0:04:20.4]} 11... Bxg4 {[%clk 0:04:08.5]} 0-1\n",
		EndTimestamp: 1659431445,
	}

	err = gamesTable.PutGameRecords([]games.GameRecord{existingGame1, existingGame2, existingGame3})
	assert.NoError(t, err)

	downloadId := uuid.New().String()
	downloadRecord := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    3,
		Failed:     0,
		Done:       3,
		Pending:    2,
		Total:      5,
	}

	err = downloadsTable.PutDownloadRecord(downloadRecord)
	assert.NoError(t, err)

	stubDownload, err := downloader.stubChessDotCom(username, "2022", "08")
	assert.NoError(t, err)

	err = wiremockClient.StubFor(stubDownload)
	assert.NoError(t, err)

	command :=
		events.SQSMessage{
			Body: fmt.Sprintf(
				`
				{
					"username": "%s",
					"userId": "%s",
					"platform": "CHESS_DOT_COM",
					"archiveId": "%s",
					"downloadId": "%s",
					"hehe": "💣"
				}
			`,
				username,
				userId,
				archiveId,
				downloadId,
			),
			MessageId: "1",
		}

	actualCommandsProcessed, err := downloader.Download(events.SQSEvent{Records: []events.SQSMessage{command}})
	assert.NoError(t, err)
	expectedCommandsProcessed := events.SQSEventResponse{
		BatchItemFailures: nil,
	}
	assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

	actualArchive, err := archivesTable.GetArchiveRecord(userId, archiveId)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive)

	assert.Equal(t, 6, actualArchive.Downloaded)

	startOfChecking := time.Now().UTC()

	assert.True(t, startOfChecking.After(actualArchive.DownloadedAt.ToTime()))
	assert.True(t, startOfTest.Before(actualArchive.DownloadedAt.ToTime()))

	var noKey map[string]*dynamodb.AttributeValue
	actualGames, _, err := gamesTable.QueryGames(userId, noKey, 1000)
	assert.NoError(t, err)

	expectedGames := []games.GameRecord{
		existingGame1Redownloaded,
		existingGame2Redownloaded,
		existingGame3Redownloaded,
		newGame1,
		newGame2,
		newGame3,
	}

	assert.Equal(t, 6, len(actualGames))
	assert.ElementsMatch(t, expectedGames, actualGames)

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownload)

	expectedDownload := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    4,
		Failed:     0,
		Done:       4,
		Pending:    1,
		Total:      5,
	}

	assert.Equal(t, expectedDownload, *actualDownload)

	verifyDownloadedCall, err := wiremockClient.Verify(stubDownload.Request(), 1)
	assert.NoError(t, err)
	assert.True(t, verifyDownloadedCall)
}

func Test_when_archive_is_not_downloaded_CommitDownloader_should_download_all_games(t *testing.T) {
	defer wiremockClient.Reset()
	downloader.pgnFilter = IdentityPgnFilter{}

	startOfTest := time.Now().UTC()

	var err error
	username := uuid.New().String()
	userId := uuid.New().String()
	archiveId := uuid.New().String()
	archiveResource := uuid.New().String()

	archiveRecord := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		Resource:     archiveResource,
		Year:         2022,
		Month:        8,
		DownloadedAt: nil,
		Downloaded:   0,
	}

	err = archivesTable.PutArchiveRecord(archiveRecord)
	assert.NoError(t, err)

	newGame1 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53168947271",
		Resource:     "https://www.chess.com/game/live/53168947271",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"akashvishwakarma000\"]\n[Result \"1-0\"]\n[CurrentPosition \"rn2k1nr/1b1p1ppp/pqp1p3/8/1b1P4/2NBPN2/PPPB1PPP/R2QK2R b KQkq -\"]\n[Timezone \"UTC\"]\n[ECO \"A40\"]\n[ECOUrl \"https://www.chess.com/openings/Queens-Pawn-Opening-Polish-Defense\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:43:00\"]\n[WhiteElo \"997\"]\n[BlackElo \"917\"]\n[TimeControl \"300\"]\n[Termination \"tigran-c-137 won - game abandoned\"]\n[StartTime \"08:43:00\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:45:21\"]\n[Link \"https://www.chess.com/game/live/53168947271\"]\n\n1. d4 {[%clk 0:04:48.8]} 1... b5 {[%clk 0:04:54.6]} 2. Nc3 {[%clk 0:04:40.2]} 2... Bb7 {[%clk 0:04:52.4]} 3. Nxb5 {[%clk 0:04:32.6]} 3... a6 {[%clk 0:04:51.5]} 4. Nc3 {[%clk 0:04:29.1]} 4... c6 {[%clk 0:04:49.9]} 5. e3 {[%clk 0:04:26.4]} 5... Qb6 {[%clk 0:04:37.4]} 6. Nf3 {[%clk 0:04:17.2]} 6... e6 {[%clk 0:04:35.9]} 7. Bd3 {[%clk 0:04:08.4]} 7... Bb4 {[%clk 0:04:35]} 8. Bd2 {[%clk 0:03:48.5]} 1-0\n",
		EndTimestamp: 1659429921,
	}

	newGame2 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53168974013",
		Resource:     "https://www.chess.com/game/live/53168974013",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"NguoiandanhVN\"]\n[Result \"0-1\"]\n[CurrentPosition \"r3r1k1/2p1nppp/1p2p3/p7/P2P1N2/qP1QP3/5PPP/5RK1 w - -\"]\n[Timezone \"UTC\"]\n[ECO \"D31\"]\n[ECOUrl \"https://www.chess.com/openings/Queens-Gambit-Declined-Queens-Knight-Variation-3...dxc4\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:45:28\"]\n[WhiteElo \"988\"]\n[BlackElo \"983\"]\n[TimeControl \"300\"]\n[Termination \"NguoiandanhVN won by resignation\"]\n[StartTime \"08:45:28\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:49:00\"]\n[Link \"https://www.chess.com/game/live/53168974013\"]\n\n1. d4 {[%clk 0:05:00]} 1... d5 {[%clk 0:04:59.5]} 2. c4 {[%clk 0:04:58.9]} 2... e6 {[%clk 0:04:59.2]} 3. Nc3 {[%clk 0:04:56]} 3... dxc4 {[%clk 0:04:57.9]} 4. e3 {[%clk 0:04:54.8]} 4... Nf6 {[%clk 0:04:55.1]} 5. Bxc4 {[%clk 0:04:47.1]} 5... b6 {[%clk 0:04:53.8]} 6. Nf3 {[%clk 0:04:42.1]} 6... Be7 {[%clk 0:04:52.5]} 7. O-O {[%clk 0:04:34.2]} 7... O-O {[%clk 0:04:50.4]} 8. b3 {[%clk 0:04:22.8]} 8... Nc6 {[%clk 0:04:48.3]} 9. Nb1 {[%clk 0:04:17.6]} 9... a5 {[%clk 0:04:43.2]} 10. a4 {[%clk 0:04:06.8]} 10... Ba6 {[%clk 0:04:39.8]} 11. Nbd2 {[%clk 0:03:49.1]} 11... Bxc4 {[%clk 0:04:37.7]} 12. Nxc4 {[%clk 0:03:47.9]} 12... Nb4 {[%clk 0:04:36.3]} 13. Ba3 {[%clk 0:03:42.1]} 13... Ne4 {[%clk 0:04:32.3]} 14. Nfd2 {[%clk 0:03:33.6]} 14... Nxd2 {[%clk 0:04:30.6]} 15. Nxd2 {[%clk 0:03:27.5]} 15... Qd5 {[%clk 0:04:16.5]} 16. Nf3 {[%clk 0:03:11.3]} 16... Rfe8 {[%clk 0:04:14]} 17. Ne1 {[%clk 0:03:09.6]} 17... Nc6 {[%clk 0:04:06.9]} 18. Nd3 {[%clk 0:02:58.1]} 18... Bxa3 {[%clk 0:04:04.5]} 19. Rxa3 {[%clk 0:02:56.5]} 19... Ne7 {[%clk 0:03:59.6]} 20. Nf4 {[%clk 0:02:54.6]} 20... Qd6 {[%clk 0:03:54]} 21. Qd3 {[%clk 0:02:51.7]} 21... Qxa3 {[%clk 0:03:52.2]} 0-1\n",
		EndTimestamp: 1659430140,
	}

	newGame3 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53169013011",
		Resource:     "https://www.chess.com/game/live/53169013011",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"VincentVeganin\"]\n[Result \"1-0\"]\n[CurrentPosition \"5Q2/7p/6pk/1R6/p7/2P1P2P/2q2PP1/6K1 b - -\"]\n[Timezone \"UTC\"]\n[ECO \"A40\"]\n[ECOUrl \"https://www.chess.com/openings/Modern-Defense-with-1-d4-2.Nc3-Bg7\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:49:01\"]\n[WhiteElo \"997\"]\n[BlackElo \"1027\"]\n[TimeControl \"300\"]\n[Termination \"tigran-c-137 won by checkmate\"]\n[StartTime \"08:49:01\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"08:57:23\"]\n[Link \"https://www.chess.com/game/live/53169013011\"]\n\n1. d4 {[%clk 0:05:00]} 1... g6 {[%clk 0:04:59.9]} 2. Nc3 {[%clk 0:04:57.9]} 2... Bg7 {[%clk 0:04:59]} 3. Nf3 {[%clk 0:04:55.2]} 3... Nc6 {[%clk 0:04:57]} 4. e3 {[%clk 0:04:51.4]} 4... d6 {[%clk 0:04:51.6]} 5. Bb5 {[%clk 0:04:49.2]} 5... Bd7 {[%clk 0:04:46.2]} 6. O-O {[%clk 0:04:47.5]} 6... a6 {[%clk 0:04:45.4]} 7. Bxc6 {[%clk 0:04:29.2]} 7... Bxc6 {[%clk 0:04:45.3]} 8. d5 {[%clk 0:04:28.4]} 8... Bxc3 {[%clk 0:04:23.5]} 9. dxc6 {[%clk 0:04:17.4]} 9... Bxb2 {[%clk 0:04:21.5]} 10. Bxb2 {[%clk 0:03:55.5]} 10... Nf6 {[%clk 0:04:17.8]} 11. Bxf6 {[%clk 0:03:51.1]} 11... exf6 {[%clk 0:04:15.3]} 12. cxb7 {[%clk 0:03:50.2]} 12... Rb8 {[%clk 0:04:14.7]} 13. Rb1 {[%clk 0:03:49.4]} 13... O-O {[%clk 0:04:12]} 14. Rb3 {[%clk 0:03:38.7]} 14... Qd7 {[%clk 0:03:52.4]} 15. Qd4 {[%clk 0:03:27.9]} 15... c5 {[%clk 0:03:43.8]} 16. Qxf6 {[%clk 0:03:22.2]} 16... Rxb7 {[%clk 0:03:34.1]} 17. Rxb7 {[%clk 0:03:10.1]} 17... Qxb7 {[%clk 0:03:34]} 18. Qxd6 {[%clk 0:03:08.9]} 18... Rb8 {[%clk 0:03:19.7]} 19. h3 {[%clk 0:03:03.5]} 19... c4 {[%clk 0:03:15.8]} 20. c3 {[%clk 0:02:24.2]} 20... Qb2 {[%clk 0:03:00.8]} 21. Qd2 {[%clk 0:02:19]} 21... Qb6 {[%clk 0:02:38.9]} 22. Qd4 {[%clk 0:02:12.9]} 22... Qb2 {[%clk 0:02:28.9]} 23. Qxc4 {[%clk 0:01:58]} 23... a5 {[%clk 0:02:24.1]} 24. Ne5 {[%clk 0:01:46.6]} 24... Qc2 {[%clk 0:02:12.4]} 25. Qxf7+ {[%clk 0:01:44]} 25... Kh8 {[%clk 0:02:07.1]} 26. Qf6+ {[%clk 0:01:18]} 26... Kg8 {[%clk 0:02:03.9]} 27. Qe6+ {[%clk 0:01:16.5]} 27... Kh8 {[%clk 0:02:01.5]} 28. Nf7+ {[%clk 0:01:13.4]} 28... Kg8 {[%clk 0:01:58]} 29. Nh6+ {[%clk 0:00:59.3]} 29... Kg7 {[%clk 0:01:36.7]} 30. Qe5+ {[%clk 0:00:51.4]} 30... Kxh6 {[%clk 0:01:31.3]} 31. Qxb8 {[%clk 0:00:50.1]} 31... Qxa2 {[%clk 0:01:28.4]} 32. Rb1 {[%clk 0:00:43.8]} 32... a4 {[%clk 0:01:25.1]} 33. Rb5 {[%clk 0:00:41.5]} 33... Qc2 {[%clk 0:01:18]} 34. Qf8# {[%clk 0:00:37.1]} 1-0\n",
		EndTimestamp: 1659430643,
	}

	newGame4 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53169604577",
		Resource:     "https://www.chess.com/game/live/53169604577",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"N-60\"]\n[Black \"tigran-c-137\"]\n[Result \"1-0\"]\n[CurrentPosition \"5Q1k/pbp3pp/1p6/4P1q1/2B5/8/PPP3PP/R6K b - -\"]\n[Timezone \"UTC\"]\n[ECO \"C34\"]\n[ECOUrl \"https://www.chess.com/openings/Kings-Gambit-Accepted-Schallopp-Defense-4.e5\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:58:14\"]\n[WhiteElo \"1067\"]\n[BlackElo \"990\"]\n[TimeControl \"300\"]\n[Termination \"N-60 won by checkmate\"]\n[StartTime \"08:58:14\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"09:04:04\"]\n[Link \"https://www.chess.com/game/live/53169604577\"]\n\n1. e4 {[%clk 0:04:57.7]} 1... e5 {[%clk 0:04:58.9]} 2. f4 {[%clk 0:04:54.8]} 2... exf4 {[%clk 0:04:57.2]} 3. Nf3 {[%clk 0:04:53.7]} 3... Nf6 {[%clk 0:04:50.9]} 4. e5 {[%clk 0:04:50.6]} 4... Ng4 {[%clk 0:04:07.6]} 5. d4 {[%clk 0:04:46]} 5... Be7 {[%clk 0:03:50.5]} 6. Bc4 {[%clk 0:04:36.6]} 6... O-O {[%clk 0:03:44.1]} 7. Bxf4 {[%clk 0:04:33.5]} 7... d5 {[%clk 0:03:31.9]} 8. Bd3 {[%clk 0:04:29.3]} 8... f6 {[%clk 0:03:25.3]} 9. O-O {[%clk 0:04:23.3]} 9... fxe5 {[%clk 0:03:20]} 10. Bxe5 {[%clk 0:04:21.6]} 10... Nxe5 {[%clk 0:03:11.7]} 11. Nxe5 {[%clk 0:04:19.3]} 11... Nd7 {[%clk 0:02:53]} 12. Nc3 {[%clk 0:04:10.5]} 12... Nxe5 {[%clk 0:02:50.4]} 13. dxe5 {[%clk 0:04:08.7]} 13... Bc5+ {[%clk 0:02:04.9]} 14. Kh1 {[%clk 0:04:05.3]} 14... b6 {[%clk 0:01:58]} 15. Nxd5 {[%clk 0:04:02.9]} 15... Bb7 {[%clk 0:01:44.7]} 16. Nf4 {[%clk 0:03:51.8]} 16... Rxf4 {[%clk 0:01:19.7]} 17. Rxf4 {[%clk 0:03:46.7]} 17... Qg5 {[%clk 0:01:18.7]} 18. Qf1 {[%clk 0:03:30.9]} 18... Rf8 {[%clk 0:01:06.8]} 19. Rxf8+ {[%clk 0:03:25.7]} 19... Bxf8 {[%clk 0:01:04.9]} 20. Bc4+ {[%clk 0:03:21]} 20... Kh8 {[%clk 0:01:02.4]} 21. Qxf8# {[%clk 0:03:18.1]} 1-0\n",
		EndTimestamp: 1659431044,
	}

	newGame5 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53170160741",
		Resource:     "https://www.chess.com/game/live/53170160741",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"yayancito24\"]\n[Black \"tigran-c-137\"]\n[Result \"1-0\"]\n[CurrentPosition \"6k1/pp4p1/7p/3n4/2BN4/8/PP1b1PPP/5K2 b - -\"]\n[Timezone \"UTC\"]\n[ECO \"C20\"]\n[ECOUrl \"https://www.chess.com/openings/Kings-Pawn-Opening-Wayward-Queen-Kiddie-Countergambit\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"09:04:12\"]\n[WhiteElo \"898\"]\n[BlackElo \"980\"]\n[TimeControl \"300\"]\n[Termination \"yayancito24 won by resignation\"]\n[StartTime \"09:04:12\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"09:09:02\"]\n[Link \"https://www.chess.com/game/live/53170160741\"]\n\n1. e4 {[%clk 0:05:00]} 1... e5 {[%clk 0:04:59.9]} 2. Qh5 {[%clk 0:04:59.2]} 2... Nf6 {[%clk 0:04:54.3]} 3. Qxe5+ {[%clk 0:04:57.4]} 3... Qe7 {[%clk 0:04:47.1]} 4. Qxe7+ {[%clk 0:04:56.4]} 4... Bxe7 {[%clk 0:04:45.4]} 5. d3 {[%clk 0:04:56.3]} 5... O-O {[%clk 0:04:43]} 6. Bg5 {[%clk 0:04:55.6]} 6... h6 {[%clk 0:04:39.1]} 7. Bxf6 {[%clk 0:04:54.6]} 7... Bxf6 {[%clk 0:04:38.1]} 8. c3 {[%clk 0:04:53.7]} 8... c6 {[%clk 0:04:32.5]} 9. Nf3 {[%clk 0:04:53]} 9... d5 {[%clk 0:04:30.8]} 10. e5 {[%clk 0:04:48.2]} 10... Re8 {[%clk 0:04:25.5]} 11. d4 {[%clk 0:04:47.1]} 11... Bg4 {[%clk 0:04:10.1]} 12. Be2 {[%clk 0:04:45.3]} 12... Bxf3 {[%clk 0:04:08.3]} 13. Bxf3 {[%clk 0:04:44.3]} 13... Bg5 {[%clk 0:03:37.6]} 14. Na3 {[%clk 0:04:39]} 14... f6 {[%clk 0:03:36.5]} 15. O-O {[%clk 0:04:31.9]} 15... fxe5 {[%clk 0:03:33.8]} 16. dxe5 {[%clk 0:04:30.9]} 16... Rxe5 {[%clk 0:03:30.4]} 17. Rfe1 {[%clk 0:04:30.8]} 17... Rxe1+ {[%clk 0:03:17.8]} 18. Rxe1 {[%clk 0:04:30.7]} 18... Nd7 {[%clk 0:03:12.4]} 19. Nc2 {[%clk 0:04:21.9]} 19... Nb6 {[%clk 0:02:35.5]} 20. Nd4 {[%clk 0:04:19.6]} 20... c5 {[%clk 0:02:28.5]} 21. Ne6 {[%clk 0:04:17.8]} 21... Bd2 {[%clk 0:02:24.9]} 22. Re2 {[%clk 0:04:13.9]} 22... Re8 {[%clk 0:02:21]} 23. Kf1 {[%clk 0:04:11.5]} 23... d4 {[%clk 0:01:58.2]} 24. cxd4 {[%clk 0:04:04.2]} 24... cxd4 {[%clk 0:01:54.3]} 25. Nxd4 {[%clk 0:04:03]} 25... Rxe2 {[%clk 0:01:46.3]} 26. Bxe2 {[%clk 0:03:59.8]} 26... Nd5 {[%clk 0:01:41.2]} 27. Bc4 {[%clk 0:03:56.5]} 1-0\n",
		EndTimestamp: 1659431342,
	}

	newGame6 := games.GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53170213967",
		Resource:     "https://www.chess.com/game/live/53170213967",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"philoz87\"]\n[Black \"tigran-c-137\"]\n[Result \"0-1\"]\n[CurrentPosition \"r2q1rk1/ppp2pp1/2n4B/3pP3/2B3b1/8/PPP2PPP/RN3RK1 w - -\"]\n[Timezone \"UTC\"]\n[ECO \"C42\"]\n[ECOUrl \"https://www.chess.com/openings/Petrovs-Defense-Urusov-Gambit\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"09:09:05\"]\n[WhiteElo \"999\"]\n[BlackElo \"989\"]\n[TimeControl \"300\"]\n[Termination \"tigran-c-137 won by resignation\"]\n[StartTime \"09:09:05\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"09:10:45\"]\n[Link \"https://www.chess.com/game/live/53170213967\"]\n\n1. e4 {[%clk 0:05:00]} 1... e5 {[%clk 0:04:59.9]} 2. Bc4 {[%clk 0:04:58.1]} 2... Nf6 {[%clk 0:04:55.5]} 3. Nf3 {[%clk 0:04:56.6]} 3... h6 {[%clk 0:04:54.4]} 4. d4 {[%clk 0:04:55.5]} 4... exd4 {[%clk 0:04:49.1]} 5. Nxd4 {[%clk 0:04:52.6]} 5... Bc5 {[%clk 0:04:46.4]} 6. O-O {[%clk 0:04:45.6]} 6... O-O {[%clk 0:04:44.2]} 7. e5 {[%clk 0:04:42.1]} 7... Bxd4 {[%clk 0:04:38.7]} 8. Qxd4 {[%clk 0:04:40.4]} 8... Nc6 {[%clk 0:04:37.6]} 9. Qf4 {[%clk 0:04:30.6]} 9... Ng4 {[%clk 0:04:13.3]} 10. Qxg4 {[%clk 0:04:27.5]} 10... d5 {[%clk 0:04:12.4]} 11. Bxh6 {[%clk 0:04:20.4]} 11... Bxg4 {[%clk 0:04:08.5]} 0-1\n",
		EndTimestamp: 1659431445,
	}

	downloadId := uuid.New().String()
	downloadRecord := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    3,
		Failed:     0,
		Done:       3,
		Pending:    2,
		Total:      5,
	}

	err = downloadsTable.PutDownloadRecord(downloadRecord)
	assert.NoError(t, err)

	stubDownload, err := downloader.stubChessDotCom(username, "2022", "08")
	assert.NoError(t, err)

	err = wiremockClient.StubFor(stubDownload)
	assert.NoError(t, err)

	command :=
		events.SQSMessage{
			Body: fmt.Sprintf(
				`
				{
					"username": "%s",
					"userId": "%s",
					"platform": "CHESS_DOT_COM",
					"archiveId": "%s",
					"downloadId": "%s",
					"hehe": "💣"
				}
			`,
				username,
				userId,
				archiveId,
				downloadId,
			),
			MessageId: "1",
		}

	actualCommandsProcessed, err := downloader.Download(events.SQSEvent{Records: []events.SQSMessage{command}})
	assert.NoError(t, err)

	expectedCommandsProcessed := events.SQSEventResponse{
		BatchItemFailures: nil,
	}
	assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

	actualArchive, err := archivesTable.GetArchiveRecord(userId, archiveId)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive)

	startOfChecking := time.Now().UTC()

	assert.Equal(t, 6, actualArchive.Downloaded)
	assert.True(t, startOfChecking.After(actualArchive.DownloadedAt.ToTime()))
	assert.True(t, startOfTest.Before(actualArchive.DownloadedAt.ToTime()))

	var noKey map[string]*dynamodb.AttributeValue
	actualGames, _, err := gamesTable.QueryGames(userId, noKey, 1000)
	assert.NoError(t, err)

	expectedGames := []games.GameRecord{
		newGame1,
		newGame2,
		newGame3,
		newGame4,
		newGame5,
		newGame6,
	}

	assert.ElementsMatch(t, expectedGames, actualGames)

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownload)

	expectedDownload := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    4,
		Failed:     0,
		Done:       4,
		Pending:    1,
		Total:      5,
	}

	assert.Equal(t, expectedDownload, *actualDownload)

	verifyDownloadedCall, err := wiremockClient.Verify(stubDownload.Request(), 1)
	assert.NoError(t, err)
	assert.True(t, verifyDownloadedCall)
}

func Test_when_archive_is_fully_downloaded_CommitDownloader_should_skip_the_process(t *testing.T) {
	defer wiremockClient.Reset()
	downloader.pgnFilter = IdentityPgnFilter{}

	var err error
	username := uuid.New().String()
	userId := uuid.New().String()

	archiveResource := fmt.Sprintf("http://0.0.0.0:18443/pub/player/%s/2022/08", username)
	archiveId := archiveResource
	assert.NoError(t, err)

	lastDownloadedAt := db.Zuludatetime(time.Date(2023, 8, 2, 8, 45, 21, 0, time.UTC))

	archiveRecord := archives.ArchiveRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		Resource:     archiveResource,
		Year:         2022,
		Month:        8,
		DownloadedAt: &lastDownloadedAt,
		Downloaded:   6,
	}

	err = archivesTable.PutArchiveRecord(archiveRecord)
	assert.NoError(t, err)

	downloadId := uuid.New().String()
	downloadRecord := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    3,
		Failed:     0,
		Done:       3,
		Pending:    2,
		Total:      5,
	}

	err = downloadsTable.PutDownloadRecord(downloadRecord)
	assert.NoError(t, err)

	stubDownload, err := downloader.stubChessDotCom(username, "2022", "08")
	assert.NoError(t, err)

	err = wiremockClient.StubFor(stubDownload)
	assert.NoError(t, err)

	command :=
		events.SQSMessage{
			Body: fmt.Sprintf(
				`
				{
					"username": "%s",
					"userId": "%s",
					"platform": "CHESS_DOT_COM",
					"archiveId": "%s",
					"downloadId": "%s",
					"hehe": "💣"
				}
			`,
				username,
				userId,
				archiveId,
				downloadId,
			),
			MessageId: "1",
		}

	actualCommandsProcessed, err := downloader.Download(events.SQSEvent{Records: []events.SQSMessage{command}})
	assert.NoError(t, err)

	expectedCommandsProcessed := events.SQSEventResponse{
		BatchItemFailures: nil,
	}
	assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

	actualArchive, err := archivesTable.GetArchiveRecord(userId, archiveId)
	assert.NoError(t, err)
	assert.NotNil(t, actualArchive)

	assert.Equal(t, 6, actualArchive.Downloaded)
	assert.Equal(t, lastDownloadedAt, *actualArchive.DownloadedAt)

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownload)

	expectedDownload := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    4,
		Failed:     0,
		Done:       4,
		Pending:    1,
		Total:      5,
	}

	assert.Equal(t, expectedDownload, *actualDownload)

	verifyDownloadedCall, err := wiremockClient.Verify(stubDownload.Request(), 0)
	assert.NoError(t, err)
	assert.True(t, verifyDownloadedCall)
}

func Test_when_archive_does_not_exists_CommitDownloader_should_skip_the_process(t *testing.T) {
	defer wiremockClient.Reset()
	downloader.pgnFilter = IdentityPgnFilter{}

	var err error
	username := uuid.New().String()
	userId := uuid.New().String()
	archiveId := uuid.New().String()

	downloadId := uuid.New().String()
	downloadRecord := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    3,
		Failed:     0,
		Done:       3,
		Pending:    2,
		Total:      5,
	}

	err = downloadsTable.PutDownloadRecord(downloadRecord)
	assert.NoError(t, err)

	stubDownload, err := downloader.stubChessDotCom(username, "2022", "08")
	assert.NoError(t, err)

	err = wiremockClient.StubFor(stubDownload)
	assert.NoError(t, err)

	command :=
		events.SQSMessage{
			Body: fmt.Sprintf(
				`
				{
					"username": "%s",
					"userId": "%s",
					"platform": "CHESS_DOT_COM",
					"archiveId": "%s",
					"downloadId": "%s",
					"hehe": "💣"
				}
			`,
				username,
				userId,
				archiveId,
				downloadId,
			),
			MessageId: "1",
		}

	actualCommandsProcessed, err := downloader.Download(events.SQSEvent{Records: []events.SQSMessage{command}})
	assert.NoError(t, err)

	expectedCommandsProcessed := events.SQSEventResponse{
		BatchItemFailures: nil,
	}
	assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

	actualDownload, err := downloadsTable.GetDownloadRecord(downloadId)
	assert.NoError(t, err)
	assert.NotNil(t, actualDownload)

	expectedDownload := downloads.DownloadRecord{
		DownloadId: downloadId,
		Succeed:    4,
		Failed:     0,
		Done:       4,
		Pending:    1,
		Total:      5,
	}

	assert.Equal(t, expectedDownload, *actualDownload)

	verifyDownloadedCall, err := wiremockClient.Verify(stubDownload.Request(), 0)
	assert.NoError(t, err)
	assert.True(t, verifyDownloadedCall)
}

func (downloader GameDownloader) stubChessDotCom(username string, year string, month string) (rule *wiremock.StubRule, err error) {
	file, err := os.Open("testdata/2022-08_few_games.json")
	if err != nil {
		return
	}
	defer file.Close()

	data, err := io.ReadAll(file)
	if err != nil {
		return
	}

	responseString := string(data)
	rule = wiremock.Get(wiremock.URLPathEqualTo(fmt.Sprintf("/pub/player/%s/games/%s/%s", username, year, month))).
		WithHeader("Accept", wiremock.EqualTo("application/json")).
		WillReturnResponse(
			wiremock.NewResponse().
				WithStatus(http.StatusOK).
				WithHeader("Content-Type", "application/json").
				WithBody(responseString),
		)
	return
}
