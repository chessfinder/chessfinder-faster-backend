package chessfinder
package core

import munit.FunSuite
import munit.ScalaCheckSuite
import org.scalacheck.Prop
import org.scalacheck.Arbitrary
import Arbitraries.given
import munit.ScalaCheckSuite
import munit.Clue.generate

import chess.bitboard.Board
import chess.bitboard.Bitboard.*

import chess.format.Fen
import chess.format.pgn.Reader
import chess.format.pgn.PgnStr
import chess.format.pgn.Reader.Result.Complete
import chess.format.pgn.Reader.Result.Incomplete
import chess.ErrorStr.value
import chess.Replay

import core.ProbabilisticPiece.{ CertainPiece, CertainlyOccupied, ProbablyOccupied }
import core.β.Ext.*
import core.β
import util.{ DescriptionHelper, βUnsafeExt }
import core.*

import core.SearchFen
class FinderTest extends FunSuite with βUnsafeExt with DescriptionHelper:

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
