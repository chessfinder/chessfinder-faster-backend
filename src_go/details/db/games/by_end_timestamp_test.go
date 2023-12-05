package games

import (
	"testing"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

func Test_IatestGameIndex_should_get_the_latest_downloaded_game_for_the_given_archive(t *testing.T) {

	var err error
	userId := uuid.New().String()
	archiveId := uuid.New().String()

	newGame1 := GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53169604577",
		Resource:     "https://www.chess.com/game/live/53169604577",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"N-60\"]\n[Black \"tigran-c-137\"]\n[Result \"1-0\"]\n[CurrentPosition \"5Q1k/pbp3pp/1p6/4P1q1/2B5/8/PPP3PP/R6K b - -\"]\n[Timezone \"UTC\"]\n[ECO \"C34\"]\n[ECOUrl \"https://www.chess.com/openings/Kings-Gambit-Accepted-Schallopp-Defense-4.e5\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"08:58:14\"]\n[WhiteElo \"1067\"]\n[BlackElo \"990\"]\n[TimeControl \"300\"]\n[Termination \"N-60 won by checkmate\"]\n[StartTime \"08:58:14\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"09:04:04\"]\n[Link \"https://www.chess.com/game/live/53169604577\"]\n\n1. e4 {[%clk 0:04:57.7]} 1... e5 {[%clk 0:04:58.9]} 2. f4 {[%clk 0:04:54.8]} 2... exf4 {[%clk 0:04:57.2]} 3. Nf3 {[%clk 0:04:53.7]} 3... Nf6 {[%clk 0:04:50.9]} 4. e5 {[%clk 0:04:50.6]} 4... Ng4 {[%clk 0:04:07.6]} 5. d4 {[%clk 0:04:46]} 5... Be7 {[%clk 0:03:50.5]} 6. Bc4 {[%clk 0:04:36.6]} 6... O-O {[%clk 0:03:44.1]} 7. Bxf4 {[%clk 0:04:33.5]} 7... d5 {[%clk 0:03:31.9]} 8. Bd3 {[%clk 0:04:29.3]} 8... f6 {[%clk 0:03:25.3]} 9. O-O {[%clk 0:04:23.3]} 9... fxe5 {[%clk 0:03:20]} 10. Bxe5 {[%clk 0:04:21.6]} 10... Nxe5 {[%clk 0:03:11.7]} 11. Nxe5 {[%clk 0:04:19.3]} 11... Nd7 {[%clk 0:02:53]} 12. Nc3 {[%clk 0:04:10.5]} 12... Nxe5 {[%clk 0:02:50.4]} 13. dxe5 {[%clk 0:04:08.7]} 13... Bc5+ {[%clk 0:02:04.9]} 14. Kh1 {[%clk 0:04:05.3]} 14... b6 {[%clk 0:01:58]} 15. Nxd5 {[%clk 0:04:02.9]} 15... Bb7 {[%clk 0:01:44.7]} 16. Nf4 {[%clk 0:03:51.8]} 16... Rxf4 {[%clk 0:01:19.7]} 17. Rxf4 {[%clk 0:03:46.7]} 17... Qg5 {[%clk 0:01:18.7]} 18. Qf1 {[%clk 0:03:30.9]} 18... Rf8 {[%clk 0:01:06.8]} 19. Rxf8+ {[%clk 0:03:25.7]} 19... Bxf8 {[%clk 0:01:04.9]} 20. Bc4+ {[%clk 0:03:21]} 20... Kh8 {[%clk 0:01:02.4]} 21. Qxf8# {[%clk 0:03:18.1]} 1-0\n",
		EndTimestamp: 1659431044,
	}

	newGame2 := GameRecord{
		UserId:       userId,
		ArchiveId:    archiveId,
		GameId:       "https://www.chess.com/game/live/53170160741",
		Resource:     "https://www.chess.com/game/live/53170160741",
		Pgn:          "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.08.02\"]\n[Round \"-\"]\n[White \"yayancito24\"]\n[Black \"tigran-c-137\"]\n[Result \"1-0\"]\n[CurrentPosition \"6k1/pp4p1/7p/3n4/2BN4/8/PP1b1PPP/5K2 b - -\"]\n[Timezone \"UTC\"]\n[ECO \"C20\"]\n[ECOUrl \"https://www.chess.com/openings/Kings-Pawn-Opening-Wayward-Queen-Kiddie-Countergambit\"]\n[UTCDate \"2022.08.02\"]\n[UTCTime \"09:04:12\"]\n[WhiteElo \"898\"]\n[BlackElo \"980\"]\n[TimeControl \"300\"]\n[Termination \"yayancito24 won by resignation\"]\n[StartTime \"09:04:12\"]\n[EndDate \"2022.08.02\"]\n[EndTime \"09:09:02\"]\n[Link \"https://www.chess.com/game/live/53170160741\"]\n\n1. e4 {[%clk 0:05:00]} 1... e5 {[%clk 0:04:59.9]} 2. Qh5 {[%clk 0:04:59.2]} 2... Nf6 {[%clk 0:04:54.3]} 3. Qxe5+ {[%clk 0:04:57.4]} 3... Qe7 {[%clk 0:04:47.1]} 4. Qxe7+ {[%clk 0:04:56.4]} 4... Bxe7 {[%clk 0:04:45.4]} 5. d3 {[%clk 0:04:56.3]} 5... O-O {[%clk 0:04:43]} 6. Bg5 {[%clk 0:04:55.6]} 6... h6 {[%clk 0:04:39.1]} 7. Bxf6 {[%clk 0:04:54.6]} 7... Bxf6 {[%clk 0:04:38.1]} 8. c3 {[%clk 0:04:53.7]} 8... c6 {[%clk 0:04:32.5]} 9. Nf3 {[%clk 0:04:53]} 9... d5 {[%clk 0:04:30.8]} 10. e5 {[%clk 0:04:48.2]} 10... Re8 {[%clk 0:04:25.5]} 11. d4 {[%clk 0:04:47.1]} 11... Bg4 {[%clk 0:04:10.1]} 12. Be2 {[%clk 0:04:45.3]} 12... Bxf3 {[%clk 0:04:08.3]} 13. Bxf3 {[%clk 0:04:44.3]} 13... Bg5 {[%clk 0:03:37.6]} 14. Na3 {[%clk 0:04:39]} 14... f6 {[%clk 0:03:36.5]} 15. O-O {[%clk 0:04:31.9]} 15... fxe5 {[%clk 0:03:33.8]} 16. dxe5 {[%clk 0:04:30.9]} 16... Rxe5 {[%clk 0:03:30.4]} 17. Rfe1 {[%clk 0:04:30.8]} 17... Rxe1+ {[%clk 0:03:17.8]} 18. Rxe1 {[%clk 0:04:30.7]} 18... Nd7 {[%clk 0:03:12.4]} 19. Nc2 {[%clk 0:04:21.9]} 19... Nb6 {[%clk 0:02:35.5]} 20. Nd4 {[%clk 0:04:19.6]} 20... c5 {[%clk 0:02:28.5]} 21. Ne6 {[%clk 0:04:17.8]} 21... Bd2 {[%clk 0:02:24.9]} 22. Re2 {[%clk 0:04:13.9]} 22... Re8 {[%clk 0:02:21]} 23. Kf1 {[%clk 0:04:11.5]} 23... d4 {[%clk 0:01:58.2]} 24. cxd4 {[%clk 0:04:04.2]} 24... cxd4 {[%clk 0:01:54.3]} 25. Nxd4 {[%clk 0:04:03]} 25... Rxe2 {[%clk 0:01:46.3]} 26. Bxe2 {[%clk 0:03:59.8]} 26... Nd5 {[%clk 0:01:41.2]} 27. Bc4 {[%clk 0:03:56.5]} 1-0\n",
		EndTimestamp: 1659431342,
	}

	actualMarshalledItems1, err := dynamodbattribute.MarshalMap(newGame1)
	assert.NoError(t, err)
	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(gamesTableName),
		Item:      actualMarshalledItems1,
	})
	assert.NoError(t, err)

	actualMarshalledItems2, err := dynamodbattribute.MarshalMap(newGame2)
	assert.NoError(t, err)
	_, err = dynamodbClient.PutItem(&dynamodb.PutItemInput{
		TableName: aws.String(gamesTableName),
		Item:      actualMarshalledItems2,
	})
	assert.NoError(t, err)

	latestGame, err := latestGameIndex.QueryByEndTimestamp(archiveId)
	assert.NoError(t, err)
	assert.NotNil(t, latestGame)

	assert.Equal(t, newGame2, *latestGame)

}

func Test_IatestGameIndex_should_return_nil_if_there_is_no_game_for_the_given_archive(t *testing.T) {

	var err error
	archiveId := uuid.New().String()

	latestGame, err := latestGameIndex.QueryByEndTimestamp(archiveId)
	assert.NoError(t, err)
	assert.Nil(t, latestGame)

}
