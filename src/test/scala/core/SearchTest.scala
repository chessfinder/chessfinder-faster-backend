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
import core.error.βExt.*
import core.error.β
import util.{ DescriptionHelper }
import util.βUnsafeExt
import core.format.*

class SearchTest extends FunSuite with βUnsafeExt with DescriptionHelper:

  val search = new Search.Impl()

  test("""
  Search for the board the game
  //// THE BOARD ///
  for the game
  //// THE GAME ///
  should succeed
  """.aline.ignore) {

    val searchFen = SearchFen("")

    val board = SearchFenReader.read(searchFen).get

    val png = PgnStr("")

    val replay =
      β
        .fromValidated {
          Reader
            .full(png)
            .leftMap(_.value)
        }
        .andThen {
          case Complete(replay)            => replay.validated
          case Incomplete(replay, failure) => (failure.value: String).failed[Replay]
        }
        .get

    val result = search.find(replay, board).get
    assertEquals(result, true)
  }

  test("""
  Search for the board the game
  //// THE BOARD ///
  for the game
  //// THE GAME ///
  should fail
  """.aline.ignore) {

    val searchFen = SearchFen("")

    val board = SearchFenReader.read(searchFen).get

    val png = PgnStr("")

    val replay: Replay =
      β
        .fromValidated {
          Reader
            .full(png)
            .leftMap(_.value)
        }
        .andThen {
          case Complete(replay)            => replay.validated
          case Incomplete(replay, failure) => (failure.value: String).failed[Replay]
        }
        .get

    val result = search.find(replay, board).get

    assertEquals(result, true)
  }
