package main

import (
	"math/rand"
	"regexp"
	"strings"
	"time"

	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/metrics"
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

const commentsPattern = `(\{[^}]*\})|\([^)]*\)`

var compiledCommentsPattern = regexp.MustCompile(commentsPattern)

const balkcsMovePattern = `\d+\.\.\.\s+`

var compiledBalkcsMovePattern = regexp.MustCompile(balkcsMovePattern)

type PgnSqueezer struct {
}

func (squeezer PgnSqueezer) Filter(pgn string) (squeezedPgn string, err error) {
	squeezedPgn = stripTagPairs(pgn)
	squeezedPgn = compiledCommentsPattern.ReplaceAllString(squeezedPgn, "")
	squeezedPgn = compiledBalkcsMovePattern.ReplaceAllString(squeezedPgn, "")
	squeezedPgn = strings.ReplaceAll(squeezedPgn, "\n", " ")
	squeezedPgn = strings.ReplaceAll(squeezedPgn, "\r", " ")

	fields := strings.Fields(squeezedPgn)
	squeezedPgn = strings.Join(fields, " ")
	squeezedPgn = strings.TrimSpace(squeezedPgn)
	return
}

func stripTagPairs(pgn string) string {
	lines := strings.Split(pgn, "\n")
	cp := []string{}
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line != "" && !strings.HasPrefix(line, "[") {
			cp = append(cp, line)
		}
	}
	return strings.Join(cp, "\n")
}

type AlternatingPgnFilter struct {
	pgnFilterMeter         *metrics.PgnFilterMeter
	TagAndCommentPgnFilter TagAndCommentPgnFilter
	PgnSqueezer            PgnSqueezer
}

func (filter AlternatingPgnFilter) Filter(pgn string) (filteredPgn string, err error) {
	chooseSqueezer := rand.Intn(2) == 0

	if chooseSqueezer {
		start := time.Now()
		filteredPgn, err = filter.PgnSqueezer.Filter(pgn)
		duration := time.Since(start)
		err = filter.pgnFilterMeter.PgnFilterStatistics(metrics.MyOwn, duration)
	} else {
		start := time.Now()
		filteredPgn, err = filter.TagAndCommentPgnFilter.Filter(pgn)
		duration := time.Since(start)
		err = filter.pgnFilterMeter.PgnFilterStatistics(metrics.NotNil, duration)
	}

	return
}
