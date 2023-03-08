package chessfinder
package core.format

import cats.syntax.all.*
import munit.FunSuite
import munit.ScalaCheckSuite
import org.scalacheck.Prop
import org.scalacheck.Arbitrary
import chess.bitboard.Bitboard.*
import chess.bitboard.Board
import chess.format.Fen

import core.Arbitraries.given
import munit.ScalaCheckSuite
import chessfinder.search.*
import munit.Clue.generate
import core.ProbabilisticPiece.{ CertainPiece, CertainlyOccupied, ProbablyOccupied }
import util.DescriptionHelper

class SearchFenReaderTest extends FunSuite with DescriptionHelper:

  test("""
  Search for the board the game
  //// THE BOARD ///
  for the game
  //// THE GAME ///
  should succeed
  """.aline.ignore) {

    val searchFen = SearchFen("")

    val actualBoard   = SearchFenReader.read(searchFen).get
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
