package chessfinder
package core

import core.Arbitraries.given
import core.ProbabilisticPiece.{ CertainPiece, CertainlyOccupied, ProbablyOccupied }
import core.SearchFen
import search.*
import util.{ DescriptionHelper, WalidatedUnsafeExt }

import cats.syntax.all.*
import chess.bitboard.Bitboard.*
import chess.bitboard.Board
import chess.format.Fen
import munit.Clue.generate
import munit.{ FunSuite, ScalaCheckSuite }
import org.scalacheck.{ Arbitrary, Prop }

class SearchFenTest extends FunSuite with DescriptionHelper:

  test("""
  Search for the board the game
  //// THE BOARD ///
  for the game
  //// THE GAME ///
  should succeed
  """.aline.ignore) {

    val searchFen = SearchFen("")

    val actualBoard   = SearchFen.read(searchFen).get
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
