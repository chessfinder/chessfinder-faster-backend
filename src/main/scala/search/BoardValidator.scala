package chessfinder
package search

import core.{ ProbabilisticBoard, SearchFen }
import search.BrokenLogic.InvalidSearchBoard
import search.entity.*

import zio.{ ZIO, ZLayer }

trait BoardValidator:

  def validate(board: SearchFen): φ[ProbabilisticBoard]

object BoardValidator:

  class Impl() extends BoardValidator:
    def validate(board: SearchFen): φ[ProbabilisticBoard] =
      SearchFen
        .read(board)
        .fold(_ => ZIO.fail(InvalidSearchBoard), ZIO.succeed)

  object Impl:
    val layer = ZLayer.succeed(BoardValidator.Impl())
