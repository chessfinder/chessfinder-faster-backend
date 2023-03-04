package chess
package search

import cats.syntax.all.*
import munit.FunSuite
import munit.ScalaCheckSuite
import org.lichess.compression.game.Bitboard as CBB
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

class ProbabilisticBoardTest extends ScalaCheckSuite:

  import scala.language.implicitConversions
  given Conversion[Pos, Int] = _.value

  property("all realisitc guesses should succeed") {
    val boards: Seq[Board] =
      FenFixtures.fens
        .map { str => Fen.read(str).getOrElse(throw RuntimeException("boooo")).board.board }

    val props = boards.map { (board: Board) =>
      given Board = board
      Prop.forAll { (guess: RealisitcGuess) =>
        guess.probabilisticBoard.includes(board)
      }
    }

    Prop.all(props*)
  }

  property("all wrong guesses should fail") {
    val boards: Seq[Board] =
      FenFixtures.fens
        .map { str => Fen.read(str).getOrElse(throw RuntimeException("boooo")).board.board }

    val props = boards.map { (board: Board) =>
      given Board = board
      Prop.forAll { (wrongGuess: WrongGuess) =>
        !wrongGuess.probabilisticBoard.includes(board)
      }
    }

    Prop.all(props*)
  }
