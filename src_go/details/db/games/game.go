package games

import "fmt"

type GameRecord struct {
	UserId       string `dynamodbav:"user_id"`
	ArchiveId    string `dynamodbav:"archive_id"`
	GameId       string `dynamodbav:"game_id"`
	Resource     string `dynamodbav:"resource"`
	Pgn          string `dynamodbav:"pgn"`
	EndTimestamp int64  `dynamodbav:"end_timestamp"`
}

func (game GameRecord) String() string {
	repr := fmt.Sprintf(
		"GameRecord{UserId: %s, ArchiveId: %s, GameId: %s, Resource: %s, EndTimestamp: %d}",
		game.UserId,
		game.ArchiveId,
		game.GameId,
		game.Resource,
		game.EndTimestamp,
	)
	return repr
}
