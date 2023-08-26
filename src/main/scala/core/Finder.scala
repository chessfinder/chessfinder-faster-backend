package chessfinder
package core

import core.β.Ext.*

import cats.data.Validated
import cats.data.Validated.{ invalid, valid }
import chess.*
import chess.format.Uci
import chess.variant.Variant

object Finder:

  def find(replay: Replay, probabilisticBoard: ProbabilisticBoard): Boolean =
    find(replay.setup, probabilisticBoard, replay.chronoMoves)

  private def find(
      game: Game,
      probabilisticBoard: ProbabilisticBoard,
      moves: List[MoveOrDrop]
  ): Boolean =

    @scala.annotation.tailrec
    def rec(game: Game, moves: List[MoveOrDrop]): Boolean =
      if probabilisticBoard.includes(game.situation.board.board)
      then true
      else
        moves match
          case Nil                  => false
          case (move: Move) :: rest => rec(game.apply(move), rest)
          case (drop: Drop) :: rest => rec(game.applyDrop(drop), rest)
    rec(game, moves)
