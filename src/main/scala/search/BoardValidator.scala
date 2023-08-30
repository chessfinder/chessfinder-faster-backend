package chessfinder
package search

import core.{ ProbabilisticBoard, SearchFen }
import BrokenComputation.InvalidSearchBoard
import search.*

import zio.{ ZIO, ZLayer }

trait BoardValidator:

  def validate(board: SearchFen): Computation[ProbabilisticBoard]

object BoardValidator:

  class Impl() extends BoardValidator:
    def validate(board: SearchFen): Computation[ProbabilisticBoard] =
      SearchFen
        .read(board)
        .fold(_ => ZIO.fail(InvalidSearchBoard), ZIO.succeed)

  object Impl:
    val layer = ZLayer.succeed(BoardValidator.Impl())
