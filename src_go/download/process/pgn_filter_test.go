package main

import (
	"io"
	"os"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

var pgnSqueezer = PgnSqueezer{}

func Test_when_pgn_filter_should_get_rid_of_tags_and_comments_and_new_lines_and_extra_spaces(t *testing.T) {
	var err error

	rawPgnPerFile, err := readPgnsFromDir("testdata/pgn/raw")
	assert.NoError(t, err)

	filteredPgnsFiles, err := readPgnsFromDir("testdata/pgn/filtered")
	assert.NoError(t, err)

	for fileName, rawPgn := range rawPgnPerFile {
		if expectedFilteredPgn, ok := filteredPgnsFiles[fileName]; ok {
			expectedFilteredPgn = strings.TrimSpace(expectedFilteredPgn)
			var actualFilteredPgn string
			actualFilteredPgn, err = pgnSqueezer.Filter(rawPgn)
			t.Log(fileName)
			assert.NoError(t, err)
			assert.Equal(t, expectedFilteredPgn, actualFilteredPgn)
			t.Log("passed")
		}
	}

}

func readPgnsFromDir(dir string) (pgnPerFile map[string]string, err error) {
	pgnPerFile = make(map[string]string)
	pgnFiles, err := os.ReadDir(dir)
	if err != nil {
		return
	}

	for _, pgnFile := range pgnFiles {
		var pgn string
		pgn, err = readPgnFromFile(dir, pgnFile)
		if err != nil {
			return
		}
		pgnPerFile[pgnFile.Name()] = pgn
	}

	return
}

func readPgnFromFile(dir string, pgnFile os.DirEntry) (pgn string, err error) {
	file, err := os.Open(dir + "/" + pgnFile.Name())
	if err != nil {
		return
	}
	defer file.Close()
	data, err := io.ReadAll(file)
	if err != nil {
		return
	}

	pgn = string(data)
	return
}
