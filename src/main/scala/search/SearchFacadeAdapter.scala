package chessfinder
package search

import core.{ ProbabilisticBoard, SearchFacade }

import chess.format.pgn.PgnStr
import zio.{ ZIO, ZLayer }

trait SearchFacadeAdapter:

  def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): Computation[Boolean]

object SearchFacadeAdapter:

  class Impl() extends SearchFacadeAdapter:
    def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): Computation[Boolean] =
      SearchFacade
        .find(pgn, probabilisticBoard)
        .fold(_ => ZIO.fail(BrokenComputation.InvalidGame), ZIO.succeed)

  object Impl:
    val layer = ZLayer.succeed(Impl())
