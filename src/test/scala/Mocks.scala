package chessfinder

import api.TaskStatusResponse
import client.ClientError.ProfileNotFound
import client.chess_com.ChessDotComClient
import client.chess_com.dto.*
import client.{ μ, ClientError }
import core.{ ProbabilisticBoard, SearchFen }
import search.*
import search.entity.*
import search.queue.{ BoardSearchingProducer, GameDownloadingProducer }
import search.repo.*
import sttp.model.Uri

import chess.format.pgn.PgnStr
import zio.*
import zio.mock.*

import java.time.Instant
import java.util.UUID

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

  object BoardFinderMock extends Mock[BoardFinder]:
    object Find extends Effect[(SearchFen, UserId, SearchRequestId), BrokenLogic, Unit]

    val compose: URLayer[Proxy, BoardFinder] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new BoardFinder:

          override def find(board: SearchFen, userId: UserId, searchRequestId: SearchRequestId): φ[Unit] =
            proxy(Find, board, userId, searchRequestId)

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
    object ListGames extends Effect[UserId, BrokenLogic, Seq[HistoricalGame]]
    object SaveGames extends Effect[(UserId, Seq[HistoricalGame]), BrokenLogic, Unit]

    val compose: URLayer[Proxy, GameRepo] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          override def list(user: UserId): φ[Seq[HistoricalGame]] =
            proxy(ListGames, user)

          override def save(userId: UserId, games: Seq[HistoricalGame]): φ[Unit] =
            proxy(SaveGames, userId, games)

      }

  object SearchResultRepoMock extends Mock[SearchResultRepo]:
    object GetMethod      extends Effect[SearchRequestId, BrokenLogic, SearchResult]
    object UpdateMethod   extends Effect[SearchResult, BrokenLogic, Unit]
    object InitiateMethod extends Effect[(SearchRequestId, Instant, Int), BrokenLogic, SearchResult]

    val compose: URLayer[Proxy, SearchResultRepo] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          override def initiate(
              id: SearchRequestId,
              startSearchAt: Instant,
              total: Int
          ): φ[SearchResult] = proxy(InitiateMethod, id, startSearchAt, total)

          override def get(searchRequestId: SearchRequestId): φ[SearchResult] =
            proxy(GetMethod, searchRequestId)

          override def update(searchResult: SearchResult): φ[Unit] = proxy(UpdateMethod, searchResult)

      }

  object TaskRepoMock extends Mock[TaskRepo]:
    object InitiateTask     extends Effect[(TaskId, Int), BrokenLogic, TaskStatusResponse]
    object GetTask          extends Effect[TaskId, BrokenLogic, TaskStatusResponse]
    object SuccessIncrement extends Effect[TaskId, BrokenLogic, Unit]
    object FailureIncrement extends Effect[TaskId, BrokenLogic, Unit]

    val compose: URLayer[Proxy, TaskRepo] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          override def initiate(taskId: TaskId, total: Int): φ[TaskStatusResponse] =
            proxy(InitiateTask, taskId, total)

          override def get(taskId: TaskId): φ[TaskStatusResponse] =
            proxy(GetTask, taskId)

          override def successIncrement(taskId: TaskId): φ[Unit] =
            proxy(SuccessIncrement, taskId)

          override def failureIncrement(taskId: TaskId): φ[Unit] =
            proxy(FailureIncrement, taskId)

      }

  object ArchiveRepoMock extends Mock[ArchiveRepo]:
    object GetMethod      extends Effect[(UserId, ArchiveId), BrokenLogic, ArchiveResult]
    object GetAllMethod   extends Effect[UserId, BrokenLogic, Seq[ArchiveResult]]
    object InitiateMethod extends Effect[(UserId, Seq[Uri]), BrokenLogic, Unit]
    object UpdateMethod   extends Effect[ArchiveResult, BrokenLogic, Unit]

    val compose: URLayer[Proxy, ArchiveRepo] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          override def initiate(
              userId: UserId,
              resources: Seq[Uri]
          ): φ[Unit] =
            proxy(InitiateMethod, userId, resources)

          override def get(userId: UserId, archiveId: ArchiveId): φ[ArchiveResult] =
            proxy(GetMethod, userId, archiveId)

          override def getAll(userId: UserId): φ[Seq[ArchiveResult]] =
            proxy(GetAllMethod, userId)

          override def update(archiveResult: ArchiveResult): φ[Unit] =
            proxy(UpdateMethod, archiveResult)
      }

  object GameDownloadingProducerMock extends Mock[GameDownloadingProducer]:

    object PublishMethod extends Effect[(UserIdentified, Seq[ArchiveId], TaskId), BrokenLogic, Unit]

    val compose: URLayer[Proxy, GameDownloadingProducer] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:
          override def publish(user: UserIdentified, archives: Seq[ArchiveId], taskId: TaskId): φ[Unit] =
            proxy(PublishMethod, user, archives, taskId)
      }

  object BoardSearchingProducerMock extends Mock[BoardSearchingProducer]:

    object PublishMethod extends Effect[(UserIdentified, SearchFen, SearchRequestId), BrokenLogic, Unit]

    val compose: URLayer[Proxy, BoardSearchingProducer] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:
          override def publish(
              user: UserIdentified,
              board: SearchFen,
              searchRequestId: SearchRequestId
          ): φ[Unit] =
            proxy(PublishMethod, user, board, searchRequestId)
      }

  object SearchRequestAcceptorMock extends Mock[SearchRequestAcceptor]:

    object RegisterMethod extends Effect[(SearchFen, ChessPlatform, UserName), BrokenLogic, SearchResult]
    object CheckMethod    extends Effect[SearchRequestId, BrokenLogic, SearchResult]

    val compose: URLayer[Proxy, SearchRequestAcceptor] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          def register(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult] =
            proxy(RegisterMethod, board, platform, userName)

          def check(searchId: SearchRequestId): φ[SearchResult] =
            proxy(CheckMethod, searchId)

      }
