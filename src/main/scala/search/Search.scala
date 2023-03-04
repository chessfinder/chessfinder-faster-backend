package chess
package search

import cats.data.Validated
import cats.data.Validated.{ invalid, valid }
import cats.implicits.*

import chess.Game
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
                case Validated.Valid(moveOrDrop) =>
                  val newGame = moveOrDrop.fold(game.apply, game.applyDrop)
                  rec(newGame, rest)
                  // rec(game, rest)
                case fail => fail
                // case fail => ???
                
      rec(game, ucis)
      
