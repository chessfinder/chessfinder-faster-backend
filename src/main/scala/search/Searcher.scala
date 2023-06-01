package chessfinder
package search

import aspect.Span
import core.{ PgnReader, ProbabilisticBoard, SearchFacade, SearchFen }
import search.entity.*

import cats.implicits.*
import cats.kernel.Monoid
import chess.format.pgn.PgnStr
import ornicar.scalalib.zeros.given_Zero_Option
import zio.{ ZIO, ZLayer }

trait Searcher:

  def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): φ[Boolean]

object Searcher:

  class Impl() extends Searcher:
    def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): φ[Boolean] =
      SearchFacade.find(pgn, probabilisticBoard).fold(_ => ZIO.fail(BrokenLogic.InvalidGame), ZIO.succeed)

  object Impl:
    val layer = ZLayer.succeed(Impl())
