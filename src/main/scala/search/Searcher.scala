package chessfinder
package search

import search.entity.*
import core.SearchFen
import zio.ZIO
import zio.ZLayer
import chessfinder.core.{ PgnReader, SearchFacade }
import chessfinder.core.ProbabilisticBoard
import chess.format.pgn.PgnStr

import cats.implicits.*
import cats.kernel.Monoid
import ornicar.scalalib.zeros.given_Zero_Option

trait Searcher:

  def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): φ[Boolean]

object Searcher:

  def find(board: SearchFen, platform: ChessPlatform, userName: UserName): ψ[GameFinder, SearchResult] =
    ψ.serviceWithZIO[GameFinder](_.find(board, platform, userName))

  class Impl() extends Searcher:
    def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): φ[Boolean] =
      SearchFacade.find(pgn, probabilisticBoard).fold(_ => ZIO.fail(BrokenLogic.InvalidGame), ZIO.succeed)

  object Impl:
    val layer = ZLayer.succeed(Impl())
