package main

import (
	"strings"

	"github.com/notnil/chess"
)

type PgnFilter interface {
	Filter(pgn string) (filteredPgn string, err error)
}

type IdentityPgnFilter struct{}

func (filter IdentityPgnFilter) Filter(pgn string) (filteredPgn string, err error) {
	filteredPgn = pgn
	return
}

type TagAndCommentPgnFilter struct{}

func (filter TagAndCommentPgnFilter) Filter(pgn string) (filteredPgn string, err error) {
	gameBuilder, err := chess.PGN(strings.NewReader(pgn))
	if err != nil {
		return
	}
	game := chess.NewGame(gameBuilder)
	game.RemoveAllTagPairs()
	game.RemoveAllComments()
	filteredPgn = game.String()
	return
}
