package chessfinder

import zio.*
import zio.mock.*
import search.*

import core.{ SearchFen, ProbabilisticBoard}
import chess.format.pgn.PgnStr
import chessfinder.search.entity.{User, DownloadingResult}

trait Mocks:
  object BoardValidatorMock extends Mock[BoardValidator]:
    object Validate extends Effect[SearchFen, BrokenLogic, ProbabilisticBoard]

    val compose: URLayer[Proxy, BoardValidator] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new BoardValidator:
          override def validate(board: SearchFen): φ[ProbabilisticBoard] = proxy(Validate, board)
      }

  object SearcherMock extends Mock[Searcher]:
    object Find extends Effect[(PgnStr, ProbabilisticBoard), BrokenLogic, Boolean]

    val compose: URLayer[Proxy, Searcher] = 
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new Searcher:
          override def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): φ[Boolean] = proxy(Find, pgn, probabilisticBoard)
      }

  object GameDownloaderMock extends Mock[GameDownloader]:
    object Downlaod extends Effect[User, BrokenLogic, DownloadingResult]

    val compose: URLayer[Proxy, GameDownloader] = 
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new GameDownloader:
          override def download(user: User): φ[DownloadingResult] = proxy(Downlaod, user)
      }
  
