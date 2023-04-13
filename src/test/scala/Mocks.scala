package chessfinder

import zio.*
import zio.mock.*
import search.*

import core.{ ProbabilisticBoard, SearchFen }
import chess.format.pgn.PgnStr
import chessfinder.search.entity.{ DownloadingResult, User }
import chessfinder.search.entity.UserName
import chessfinder.client.ClientError
import chessfinder.client.ClientError.ProfileNotFound
import chessfinder.client.chess_com.dto.*
import sttp.model.Uri
import chessfinder.client.chess_com.ChessDotComClient
import chessfinder.client.μ
import search.entity.*

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
          override def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): φ[Boolean] =
            proxy(Find, pgn, probabilisticBoard)
      }

  object GameDownloaderMock extends Mock[GameDownloader]:
    object Downlaod extends Effect[User, BrokenLogic, DownloadingResult]

    val compose: URLayer[Proxy, GameDownloader] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new GameDownloader:
          override def download(user: User): φ[DownloadingResult] = proxy(Downlaod, user)
      }

  object ChessDotComClientMock extends Mock[ChessDotComClient]:
    object ArchivesMethod extends Effect[UserName, ClientError, Archives]
    object ProfileMethod  extends Effect[UserName, ClientError, Profile]
    object GamesMethod    extends Effect[Uri, ClientError, Games]

    val compose: URLayer[Proxy, ChessDotComClient] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new ChessDotComClient:

          override def profile(userName: UserName): μ[Profile]   = proxy(ProfileMethod, userName)
          override def archives(userName: UserName): μ[Archives] = proxy(ArchivesMethod, userName)
          override def games(uri: Uri): μ[Games]                 = proxy(GamesMethod, uri)
      }

  object GameFinderMock extends Mock[GameFinder]:
    object Find extends Effect[(SearchFen, ChessPlatform, UserName), BrokenLogic, SearchResult]

    val compose: URLayer[Proxy, GameFinder] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new GameFinder:

          override def find(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult] =
            proxy(Find, board, platform, userName)

      }
