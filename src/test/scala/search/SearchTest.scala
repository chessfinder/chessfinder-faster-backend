package chess
package search

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
import chess.search.format.*
import chess.format.pgn.Reader
import chess.format.pgn.PgnStr
import chess.format.pgn.Reader.Result.Complete
import chess.format.pgn.Reader.Result.Incomplete
import chess.search.error.ValidationResultExt.*
import munit.Clue.generate
import chess.ErrorStr.value
import chess.search.error.ValidationResult

class SearchTest extends FunSuite with ValidationUnsafeHelper with DescriptionHelper:

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
      ValidationResult
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
      ValidationResult
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
