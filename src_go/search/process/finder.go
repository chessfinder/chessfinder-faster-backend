package main

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/games"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/db/searches"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/search/process/searcher"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

const MaxGamesPerRequest = 100
const StopSearchIfFound = 10

type BoardFinder struct {
	searchesTableName string
	gamesTableName    string
	awsConfig         *aws.Config
	searcher          searcher.BoardSearcher
}

func (finder *BoardFinder) Find(commands events.SQSEvent) (commandsProcessed events.SQSEventResponse, err error) {
	config := zap.NewProductionConfig()
	config.OutputPaths = []string{"stdout"}
	timeEncoder := func(t time.Time, enc zapcore.PrimitiveArrayEncoder) {
		enc.AppendString(t.UTC().Format("2006-01-02T15:04:05.000Z"))
	}
	config.EncoderConfig.EncodeTime = timeEncoder

	logger, err := config.Build()
	if err != nil {
		return
	}
	defer logger.Sync()
	awsSession, err := session.NewSession(finder.awsConfig)
	if err != nil {
		logger.Error("impossible to create an AWS session!")
		return
	}
	dynamodbClient := dynamodb.New(awsSession)
	chessDotComClient := &http.Client{}

	logger.Info("Processing commands in total", zap.Int("commands", len(commands.Records)))

	for _, message := range commands.Records {
		_, _ = finder.processSingle(&message, dynamodbClient, chessDotComClient, logger)
	}
	return
}

func (finder *BoardFinder) processSingle(
	message *events.SQSMessage,
	dynamodbClient *dynamodb.DynamoDB,
	chessDotComClient *http.Client,
	logger *zap.Logger,
) (commandProcessed *events.SQSBatchItemFailure, err error) {
	command := queue.SearchBoardCommand{}
	err = json.Unmarshal([]byte(message.Body), &command)
	if err != nil {
		logger.Error("impossible to unmarshal the command", zap.Error(err))
		return
	}

	logger = logger.With(zap.String("searchId", command.SearchId))
	logger = logger.With(zap.String("userId", command.UserId))
	logger = logger.With(zap.String("archiveId", command.Board))
	logger.Info("Processing command")

	logger.Info("getting the search record")
	searchRecord := searches.SearchRecord{}
	searchRecordItems, err := dynamodbClient.GetItem(&dynamodb.GetItemInput{
		TableName: aws.String(finder.searchesTableName),
		Key: map[string]*dynamodb.AttributeValue{
			"search_id": {
				S: aws.String(command.SearchId),
			},
		},
	})

	if err != nil {
		logger.Error("impossible to get the search record", zap.Error(err))
		return
	}

	if len(searchRecordItems.Item) == 0 {
		logger.Error("search record not found")
		return
	}

	err = dynamodbattribute.UnmarshalMap(searchRecordItems.Item, &searchRecord)
	if err != nil {
		logger.Error("impossible to unmarshal the search record", zap.Error(err))
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
		gameRecords := []games.GameRecord{}
		gameRecordsItems, err := dynamodbClient.Query(&dynamodb.QueryInput{
			TableName:              aws.String(finder.gamesTableName),
			KeyConditionExpression: aws.String("user_id = :user_id"),
			ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
				":user_id": {
					S: aws.String(command.UserId),
				},
			},
			Limit:             aws.Int64(MaxGamesPerRequest),
			ExclusiveStartKey: lastKey,
		})

		if err != nil {
			logger.Error("impossible to get the game records", zap.Error(err))
			return
		}

		if len(gameRecordsItems.Items) == 0 {
			logger.Info("no game records found")
			return
		}

		err = dynamodbattribute.UnmarshalListOfMaps(gameRecordsItems.Items, &gameRecords)
		if err != nil {
			logger.Error("impossible to unmarshal the game records", zap.Error(err))
			return
		}

		for _, gameRecord := range gameRecords {
			isFound, errFromSearch := finder.searcher.SearchBoard(command.Board, gameRecord.Pgn)
			totalExamined++
			if errFromSearch != nil {
				logger.Error("impossible to search the board", zap.Error(errFromSearch))
				isFound = false
			}
			if isFound {
				totalMatched = append(totalMatched, gameRecord.Resource)
			}
			if len(totalMatched) >= StopSearchIfFound {
				logger.Info("stopping the search because of the limit")
				break
			}
		}
		logger = logger.With(zap.Int("examined", totalExamined))
		logger.Info("updating the search record")
		var totalMatchedDynamodbAttribute *dynamodb.AttributeValue
		if len(totalMatched) > 0 {
			totalMatchedDynamodbAttribute = &dynamodb.AttributeValue{
				SS: aws.StringSlice(totalMatched),
			}
		} else {
			totalMatchedDynamodbAttribute = &dynamodb.AttributeValue{
				NULL: aws.Bool(true),
			}
		}

		_, err = dynamodbClient.UpdateItem(&dynamodb.UpdateItemInput{
			TableName: aws.String(finder.searchesTableName),
			Key: map[string]*dynamodb.AttributeValue{
				"search_id": {
					S: aws.String(command.SearchId),
				},
			},
			ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
				":examined": {
					N: aws.String(strconv.Itoa(totalExamined)),
				},
				":lastExaminedAt": {
					S: aws.String(now.String()),
				},
				":matched": totalMatchedDynamodbAttribute,
			},
			UpdateExpression: aws.String("SET examined = :examined, last_examined_at = :lastExaminedAt, matched = :matched"),
		})

		if err != nil {
			logger.Error("impossible to update the search record", zap.Error(err))
			return
		}

		nextKey = gameRecordsItems.LastEvaluatedKey
		return
	}

	matchedGames := []string{}
	var lastKey map[string]*dynamodb.AttributeValue

	round := 0
	examined := 0
	var errOfSerach error

	for {
		logger := logger.With(zap.Int("round", round+1))
		matchedGames, lastKey, examined, errOfSerach = getGamesAnalyseAndUpdateStatus(logger, lastKey, examined, matchedGames)
		if errOfSerach != nil {
			break
		}

		if len(matchedGames) >= StopSearchIfFound {
			logger.Info("stopping the whole search because of the limit")
			break
		}

		round++

		if len(lastKey) == 0 {
			break
		}
	}

	logger.Info("search finished")

	searchStatus := searches.SearchedAll
	if len(matchedGames) >= 10 || errOfSerach != nil {
		searchStatus = searches.SearchedPartially
	}

	logger.Info("updating the search record")

	_, err = dynamodbClient.UpdateItem(&dynamodb.UpdateItemInput{
		TableName: aws.String(finder.searchesTableName),
		Key: map[string]*dynamodb.AttributeValue{
			"search_id": {
				S: aws.String(command.SearchId),
			},
		},
		ExpressionAttributeNames: map[string]*string{
			"#status": aws.String("status"),
		},
		ExpressionAttributeValues: map[string]*dynamodb.AttributeValue{
			":status": {
				S: aws.String(string(searchStatus)),
			},
		},
		UpdateExpression: aws.String("SET #status = :status"),
	})

	if err != nil {
		logger.Error("impossible to update the search record", zap.Error(err))
		return
	}

	logger.Info("search record updated")

	return
}
