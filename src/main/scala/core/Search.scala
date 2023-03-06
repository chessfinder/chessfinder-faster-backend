package chessfinder
package core

import cats.data.Validated
import cats.data.Validated.{ invalid, valid }

import chess.{ Drop, Game, Move }
import chess.{Situation, Replay, MoveOrDrop}
import chess.variant.Variant
import chess.format.Uci
import error.β
import error.βExt.* 

trait Search:

  def find(replay: Replay, probabilisticBoard: ProbabilisticBoard): β[Boolean]

object Search:

  class Impl() extends Search:

    override def find(replay: Replay, probabilisticBoard: ProbabilisticBoard): β[Boolean] = 
      find(replay.setup, probabilisticBoard, replay.chronoMoves)

    protected def find(
        game: Game,
        probabilisticBoard: ProbabilisticBoard,
        moves: List[MoveOrDrop]
    ): β[Boolean] =

      @scala.annotation.tailrec
      def rec(game: Game, moves: List[MoveOrDrop]): β[Boolean] =
        if probabilisticBoard.includes(game.situation.board.board)
        then true.validated
        else
          moves match
            case Nil => false.validated
            case (move: Move) :: rest => rec(game.apply(move), rest)
            case (drop: Drop) :: rest => rec(game.applyDrop(drop), rest)
      rec(game, moves)
