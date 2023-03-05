package chess
package search.format

import cats.syntax.all.*
import munit.FunSuite
import munit.ScalaCheckSuite
import org.scalacheck.Prop
import org.scalacheck.Arbitrary
import chess.bitboard.Bitboard.*
import chess.bitboard.Board
import chess.format.Fen
import chess.search.FenFixtures
import chess.search.RealisitcGuess
import chess.search.WrongGuess
import chess.search.Arbitraries.given
import munit.ScalaCheckSuite
import chess.search.*
import munit.Clue.generate
import chess.search.ProbabilisticPiece.{ CertainPiece, CertainlyOccupied, ProbablyOccupied }

class SearchFenReaderTest extends FunSuite with DescriptionHelper:

  test("""
  Search for the board the game
  //// THE BOARD ///
  for the game
  //// THE GAME ///
  should succeed
  """.aline.ignore) {

    val searchFen = SearchFen("")

    val actualBoard = SearchFenReader.read(searchFen).get
    val expectedBoard = ???
    
    assertEquals(actualBoard, expectedBoard)
  }

  test("""
  Search for the board the game
  //// THE BOARD ///
  for the game
  //// THE GAME ///
  should fail
  """.aline.ignore) {

    ???
  }