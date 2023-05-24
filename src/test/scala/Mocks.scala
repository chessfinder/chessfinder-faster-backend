package chessfinder

import zio.*
import zio.mock.*
import search.*

import core.{ ProbabilisticBoard, SearchFen }
import chess.format.pgn.PgnStr
import chessfinder.search.entity.{ FetchingResult, User }
import chessfinder.search.entity.UserName
import chessfinder.client.ClientError
import chessfinder.client.ClientError.ProfileNotFound
import chessfinder.client.chess_com.dto.*
import sttp.model.Uri
import chessfinder.client.chess_com.ChessDotComClient
import chessfinder.client.μ
import search.entity.*
import chessfinder.search.repo.*
import chessfinder.api.TaskStatusResponse
import chessfinder.api.ApiVersion
import java.util.UUID
import chessfinder.search.queue.GameDownloadingProducer

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

  object AsyncGameFetcherMock extends Mock[GameFetcher[ApiVersion.Async.type]]:
    object Fetch extends Effect[User, BrokenLogic, FetchingResult]

    val compose: URLayer[Proxy, GameFetcher[ApiVersion.Async.type]] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new GameFetcher:
          override def fetch(user: User): φ[FetchingResult] = proxy(Fetch, user)
      }

  object NewbornGameFetcherMock extends Mock[GameFetcher[ApiVersion.Newborn.type]]:
    object Fetch extends Effect[User, BrokenLogic, FetchingResult]

    val compose: URLayer[Proxy, GameFetcher[ApiVersion.Newborn.type]] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new GameFetcher:
          override def fetch(user: User): φ[FetchingResult] = proxy(Fetch, user)
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

  object GameFinderMock extends Mock[GameFinder[ApiVersion.Async.type]]:
    object Find extends Effect[(SearchFen, ChessPlatform, UserName), BrokenLogic, SearchResult]

    val compose: URLayer[Proxy, GameFinder[ApiVersion.Async.type]] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new GameFinder:

          override def find(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult] =
            proxy(Find, board, platform, userName)

      }

  object UserRepoMock extends Mock[UserRepo]:
    object GetUser  extends Effect[User, BrokenLogic, UserIdentified]
    object SaveUser extends Effect[UserIdentified, BrokenLogic, Unit]

    val compose: URLayer[Proxy, UserRepo] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new UserRepo:
          override def get(user: User): φ[UserIdentified] =
            proxy(GetUser, user)

          override def save(user: UserIdentified): φ[Unit] =
            proxy(SaveUser, user)
      }

  object GameRepoMock extends Mock[GameRepo]:
    object ListGames extends Effect[UserIdentified, BrokenLogic, Seq[HistoricalGame]]
    object SaveGames extends Effect[(UserId, Seq[HistoricalGame]), BrokenLogic, Unit]

    val compose: URLayer[Proxy, GameRepo] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          override def list(user: UserIdentified): φ[Seq[HistoricalGame]] =
            proxy(ListGames, user)

          override def save(userId: UserId, games: Seq[HistoricalGame]): φ[Unit] =
            proxy(SaveGames, userId, games)

      }

  object TaskRepoMock extends Mock[TaskRepo]:
    object InitiateTask     extends Effect[(TaskId, Int), BrokenLogic, Unit]
    object GetTask          extends Effect[TaskId, BrokenLogic, TaskStatusResponse]
    object SuccessIncrement extends Effect[TaskId, BrokenLogic, Unit]
    object FailureIncrement extends Effect[TaskId, BrokenLogic, Unit]

    val compose: URLayer[Proxy, TaskRepo] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          override def initiate(taskId: TaskId, total: Int): φ[Unit] =
            proxy(InitiateTask, taskId, total)

          override def get(taskId: TaskId): φ[TaskStatusResponse] =
            proxy(GetTask, taskId)

          override def successIncrement(taskId: TaskId): φ[Unit] =
            proxy(SuccessIncrement, taskId)

          override def failureIncrement(taskId: TaskId): φ[Unit] =
            proxy(FailureIncrement, taskId)

      }

  object GameDownloadingProducerMock extends Mock[GameDownloadingProducer]:

    object PublishMethod extends Effect[(UserIdentified, Archives, TaskId), BrokenLogic, Unit]

    val compose: URLayer[Proxy, GameDownloadingProducer] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:
          override def publish(user: UserIdentified, archives: Archives, taskId: TaskId): φ[Unit] =
            proxy(PublishMethod, user, archives, taskId)
      }
