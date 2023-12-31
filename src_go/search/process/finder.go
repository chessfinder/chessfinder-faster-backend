package main

import (
	"encoding/json"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/games"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/searches"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue"
	"go.uber.org/zap"
)

const MaxGamesPerRequest = 100
const StopSearchIfFound = 10

type BoardFinder struct {
	searchesTableName string
	gamesTableName    string
	awsConfig         *aws.Config
	searcher          BoardSearcher
}

func (finder *BoardFinder) Find(commands events.SQSEvent) (events.SQSEventResponse, error) {
	logger := logging.MustCreateZuluTimeLogger()
	defer logger.Sync()
	failedEvents := queue.ProcessMultiple(commands, finder, logger)
	return failedEvents, nil
}

func (finder *BoardFinder) ProcessSingle(
	message *events.SQSMessage,
	logger *zap.Logger,
) (commandProcessed *events.SQSBatchItemFailure, err error) {

	awsSession, err := session.NewSession(finder.awsConfig)
	if err != nil {
		logger.Error("impossible to create an AWS session!")
		return
	}
	dynamodbClient := dynamodb.New(awsSession)

	command := queue.SearchBoardCommand{}
	err = json.Unmarshal([]byte(message.Body), &command)
	if err != nil {
		logger.Error("impossible to unmarshal the command", zap.Error(err))
		return
	}

	logger = logger.With(zap.String("searchId", command.SearchId))
	logger = logger.With(zap.String("userId", command.UserId))
	logger = logger.With(zap.String("board", command.Board))
	logger.Info("Processing command")

	logger.Info("getting the search record")
	searchRecord, err := searches.SearchesTable{
		Name:           finder.searchesTableName,
		DynamodbClient: dynamodbClient,
	}.GetSearchRecord(command.SearchId)

	if err != nil {
		logger.Error("impossible to get the search record", zap.Error(err))
		return
	}

	if searchRecord == nil {
		logger.Error("search record not found")
		return
	}

	if searchRecord.Status != searches.InProgress {
		logger.Info("search already processed")
		return
	}

	getGamesAnalyseAndUpdateStatus := func(
		logger *zap.Logger,
		lastKey map[string]*dynamodb.AttributeValue,
		examinedBefore int,
		matchedBefore []string,
	) (
		totalMatched []string,
		nextKey map[string]*dynamodb.AttributeValue,
		totalExamined int,
		err error,
	) {
		totalMatched = matchedBefore
		totalExamined = examinedBefore

		now := db.Zuludatetime(time.Now())
		logger.Info("getting the game records")

		gameRecords, nextKey, err := games.GamesTable{
			Name:           finder.gamesTableName,
			DynamodbClient: dynamodbClient,
		}.QueryGames(command.UserId, lastKey, MaxGamesPerRequest)

		if err != nil {
			logger.Error("impossible to get the game records", zap.Error(err))
			return
		}

		if len(gameRecords) == 0 {
			logger.Info("no game records found")
			return
		}

		gamePgn := make([]GamePgn, len(gameRecords))
		for i, gameRecord := range gameRecords {
			gamePgn[i] = GamePgn{
				Resource: gameRecord.GameId,
				Pgn:      gameRecord.Pgn,
			}
		}

		matchedGames, examined, errFromSearch := finder.searcher.Match(command.SearchId, command.Board, gamePgn, logger)
		totalExamined += examined
		if errFromSearch != nil {
			logger.Error("impossible to match the board", zap.Error(err))
		}
		if len(matchedGames) > 0 {
			totalMatched = append(totalMatched, matchedGames...)
		}

		logger = logger.With(zap.Int("examined", totalExamined))
		logger.Info("updating the search record")

		err = searches.SearchesTable{
			Name:           finder.searchesTableName,
			DynamodbClient: dynamodbClient,
		}.UpdateMatchings(command.SearchId, totalExamined, totalMatched, now)

		if err != nil {
			logger.Error("impossible to update the search record", zap.Error(err))
			return
		}
		return
	}

	matchedGames := []string{}
	var lastKey map[string]*dynamodb.AttributeValue

	round := 0
	examined := 0
	var errOfSearch error
	isStopped := false

	for {
		logger := logger.With(zap.Int("round", round+1))
		matchedGames, lastKey, examined, errOfSearch = getGamesAnalyseAndUpdateStatus(logger, lastKey, examined, matchedGames)
		if errOfSearch != nil {
			break
		}

		if len(matchedGames) >= StopSearchIfFound {
			logger.Info("stopping the whole search because of the limit")
			isStopped = true
			break
		}

		round++

		if len(lastKey) == 0 {
			break
		}
	}

	logger.Info("search finished")

	searchStatus := searches.SearchedAll
	if isStopped || errOfSearch != nil {
		searchStatus = searches.SearchedPartially
	}

	logger.Info("updating the search record")

	err = searches.SearchesTable{
		Name:           finder.searchesTableName,
		DynamodbClient: dynamodbClient,
	}.UpdateStatus(command.SearchId, searchStatus)

	if err != nil {
		logger.Error("impossible to update the search record", zap.Error(err))
		return
	}

	logger.Info("search record updated")

	return
}
