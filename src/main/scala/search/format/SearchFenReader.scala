package chess
package search.format

import cats.implicits.*
import cats.kernel.Monoid
import ornicar.scalalib.zeros.given_Zero_Option
import chess.search.{ ProbabilisticBoard, ProbabilisticPiece }

trait SearchFenReader:
  def read(fen: SearchFen): Option[ProbabilisticBoard] =
    val maybePosition = fen.value.trim().takeWhile(' ' !=) match
      case word if word.count('/' ==) == 7 => Some(word)
      case word                            => None

    for {
      positions <- maybePosition
      pieces = makePieces(List.empty)(positions.toList, 0, 7)
      board  = ProbabilisticBoard.fromMap(pieces.toMap)
    } yield board

  @scala.annotation.tailrec
  private def makePieces(acc: List[(Pos, ProbabilisticPiece)])(
      chars: List[Char],
      x: Int,
      y: Int
  ): List[(Pos, ProbabilisticPiece)] =
    chars match
      case Nil         => acc
      case '/' :: rest => makePieces(acc)(rest, 0, y - 1)
      case '-' :: rest => makePieces(acc)(rest, x + 1, y)
      case c :: rest =>
        val newAcc = for {
          pos   <- Pos.at(x, y)
          piece <- ProbabilisticPiece.fromChar(c)
          elem   = pos -> piece
          newAcc = elem :: acc
        } yield newAcc
        makePieces(newAcc.getOrElse(acc))(rest, x + 1, y)
