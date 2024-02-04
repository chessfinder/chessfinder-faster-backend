package main

import (
	"regexp"
	"strings"
)

type PgnFilter interface {
	Filter(pgn string) (filteredPgn string, err error)
}

type IdentityPgnFilter struct{}

func (filter IdentityPgnFilter) Filter(pgn string) (filteredPgn string, err error) {
	filteredPgn = pgn
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
