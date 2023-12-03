package chessfinder
package core

import core.Arbitraries.given
import core.ProbabilisticPiece.{ CertainPiece, CertainlyOccupied, ProbablyOccupied }
import core.{ SearchFen, Walidated, * }
import core.Walidated.Ext.*
import util.{ DescriptionHelper, WalidatedUnsafeExt }

import chess.ErrorStr.value
import chess.Replay
import chess.bitboard.Bitboard.*
import chess.bitboard.Board
import chess.format.Fen
import chess.format.pgn.Reader.Result.{ Complete, Incomplete }
import chess.format.pgn.{ PgnStr, Reader }
import munit.Clue.generate
import munit.{ FunSuite, ScalaCheckSuite }
import org.scalacheck.{ Arbitrary, Prop }

class FinderTest extends FunSuite with WalidatedUnsafeExt with DescriptionHelper:

  test("""
  Finder for the board
  //// THE BOARD ///
  for the game
  //// THE GAME ///
  should succeed
  """.aline.ignore) {

    val searchFen = SearchFen("")

    val board = SearchFen.read(searchFen).get

    val png = PgnStr("")

    val replay = PgnReader.read(png).get

    val result = Finder.find(replay, board)
    assertEquals(result, true)
  }

  test("""
  Finder for the board
  //// THE BOARD ///
  for the game
  //// THE GAME ///
  should fail
  """.aline.ignore) {

    val searchFen = SearchFen("")

    val board = SearchFen.read(searchFen).get

    val png = PgnStr("")

    val replay = PgnReader.read(png).get

    val result = Finder.find(replay, board)

    assertEquals(result, true)
  }
