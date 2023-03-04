package chess
package search

import cats.data.Validated
import cats.data.Validated.{ invalid, valid }
import cats.implicits.*

import chess.{ Drop, Game, Move }
import chess.Situation
import chess.format.Uci

trait Search:

  def find(game: Game, probabilisticBoard: ProbabilisticBoard, ucis: List[Uci]): Validated[String, Boolean]

object Search:

  class Impl() extends Search:

    override def find(
        game: Game,
        probabilisticBoard: ProbabilisticBoard,
        ucis: List[Uci]
    ): Validated[String, Boolean] =

      @scala.annotation.tailrec
      def rec(game: Game, ucis: List[Uci]): Validated[String, Boolean] =
        if probabilisticBoard.includes(game.situation.board.board)
        then valid(true)
        else
          ucis match
            case Nil => valid(false)
            case uci :: rest =>
              uci(game.situation) match
                case Validated.Valid(move: Move) => rec(game.apply(move), rest)
                case Validated.Valid(drop: Drop) => rec(game.applyDrop(drop), rest)
                case fail                        => fail.bimap(err => err.value, _ => false)
      rec(game, ucis)
