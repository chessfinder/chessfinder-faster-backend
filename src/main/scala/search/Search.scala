package chess
package search

import cats.data.Validated
import cats.data.Validated.{ invalid, valid }

import chess.{ Drop, Game, Move }
import chess.Situation
import chess.variant.Variant
import chess.format.Uci
import chess.search.error.ValidationResult
import chess.search.error.ValidationResultExt.* 

trait Search:

  def find(replay: Replay, probabilisticBoard: ProbabilisticBoard): ValidationResult[Boolean]

object Search:

  class Impl() extends Search:

    override def find(replay: Replay, probabilisticBoard: ProbabilisticBoard): ValidationResult[Boolean] = 
      find(replay.setup, probabilisticBoard, replay.chronoMoves)

    protected def find(
        game: Game,
        probabilisticBoard: ProbabilisticBoard,
        moves: List[MoveOrDrop]
    ): ValidationResult[Boolean] =

      @scala.annotation.tailrec
      def rec(game: Game, moves: List[MoveOrDrop]): ValidationResult[Boolean] =
        if probabilisticBoard.includes(game.situation.board.board)
        then true.validated
        else
          moves match
            case Nil => false.validated
            case (move: Move) :: rest => rec(game.apply(move), rest)
            case (drop: Drop) :: rest => rec(game.applyDrop(drop), rest)
      rec(game, moves)
