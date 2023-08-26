package chessfinder
package core

import core.Arbitraries.given
import core.ProbabilisticPiece.{ CertainPiece, CertainlyOccupied, ProbablyOccupied }
import search.*

import cats.syntax.all.*
import chess.*
import chess.bitboard.Bitboard.*
import chess.bitboard.Board
import chess.format.Fen
import munit.Clue.generate
import munit.{ FunSuite, ScalaCheckSuite }
import org.scalacheck.{ Arbitrary, Prop }

class ProbabilisticBoardTest extends ScalaCheckSuite:

  import scala.language.implicitConversions
  given Conversion[Pos, Int] = _.value

  property("all realisitc guesses should succeed") {
    val boards: Seq[Board] =
      FenFixtures.fens
        .map { str => Fen.read(str).getOrElse(throw RuntimeException("boooo")).board.board }

    val props = boards.map { (board: Board) =>
      given Board = board
      Prop.forAll { (guess: RealisticGuess) =>
        guess.probabilisticBoard.includes(board)
      }
    }

    Prop.all(props*)
  }

  /*
      X chessfinder.core.ProbabilisticBoardTest.all wrong guesses shouldit.FailException: C:\Users\tohanyan\IdeaProjects\chessfinder\chess-finest\scala\core\ProbabilisticBoardTest.scala:51
    50:    Prop.all(props*)ch.repo.TaskRepoTest 1s
    51:  } chessfinder.client.chess_com.ChessDotComClientTest 2s
    52: => chessfinder.core.ProbabilisticBoardTest 2s
      | => chessfinder.search.repo.GameRepoTest 2s
    Failing seed: n9YufSlugYHERKixQx4lf7zM_hqQWDulJhsu4i0bYxE=
    You can reproduce this failure by adding the following override to you

      override def scalaCheckInitialSeed = "n9YufSlugYHERKixQx4lf7zM_hqQWD

    Gave up after only 98 passed tests. 501 tests were discarded.
   */
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

  test("""
  Probabilistic board from this board
  -----rk-/-??---bp/--0??-p-/--???---/-0------/----PpP-/--0--PqP/-Q---R-K
  should be built correctly
  """.replace('\n', ' ')) {

    val pieces = Map(
      Pos.B1 -> CertainPiece(Color.White, Queen),
      Pos.F1 -> CertainPiece(Color.White, Rook),
      Pos.H1 -> CertainPiece(Color.White, King),
      Pos.C2 -> CertainlyOccupied,
      Pos.F2 -> CertainPiece(Color.White, Pawn),
      Pos.G2 -> CertainPiece(Color.Black, Queen),
      Pos.H2 -> CertainPiece(Color.White, Pawn),
      Pos.E3 -> CertainPiece(Color.White, Pawn),
      Pos.F3 -> CertainPiece(Color.Black, Pawn),
      Pos.G3 -> CertainPiece(Color.White, Pawn),
      Pos.B4 -> CertainlyOccupied,
      Pos.C5 -> ProbablyOccupied,
      Pos.D5 -> ProbablyOccupied,
      Pos.E5 -> ProbablyOccupied,
      Pos.C6 -> CertainlyOccupied,
      Pos.D6 -> ProbablyOccupied,
      Pos.E6 -> ProbablyOccupied,
      Pos.G6 -> CertainPiece(Color.Black, Pawn),
      Pos.B7 -> ProbablyOccupied,
      Pos.C7 -> ProbablyOccupied,
      Pos.G7 -> CertainPiece(Color.Black, Bishop),
      Pos.H7 -> CertainPiece(Color.Black, Pawn),
      Pos.F8 -> CertainPiece(Color.Black, Rook),
      Pos.G8 -> CertainPiece(Color.Black, King)
    )

    val expectedBoard =
      val pawns             = 0x8040000070a000L.bb
      val bishops           = 0x40000000000000L.bb
      val knights           = 0x0L.bb
      val rooks             = 0x2000000000000020L.bb
      val queens            = 0x4002L.bb
      val kings             = 0x4000000000000080L.bb
      val whites            = 0x50a0a2L.bb
      val blacks            = 0x60c0400000204000L.bb
      val occupiedByKnown   = 0x60c040000070e0a2L.bb
      val occupiedByUnknown = 0x40002000400L.bb
      val maybeOccupied     = 0x6181c00000000L.bb
      val certainBoard = Board(
        pawns = pawns,
        knights = knights,
        bishops = bishops,
        rooks = rooks,
        queens = queens,
        kings = kings,
        white = whites,
        black = blacks,
        occupied = occupiedByKnown
      )
      ProbabilisticBoard(
        certainBoard = certainBoard,
        certainlyOccupiedByUnknown = occupiedByUnknown,
        maybeOccupied = maybeOccupied
      )

    val actualBoard = ProbabilisticBoard.fromMap(pieces)
    assertEquals(actualBoard, expectedBoard)
  }
