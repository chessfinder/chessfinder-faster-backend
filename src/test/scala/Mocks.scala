package chessfinder

import client.chess_com.{ Archives, ChessDotComClient, Games, Profile }
import client.{ Call, ClientError }
import core.{ ProbabilisticBoard, SearchFen }
import download.ArchiveResult
import download.details.*
import search.*
import search.details.{ GameFetcher, SearchBoardCommandPublisher, SearchResultRepo, UserFetcher }

import chess.format.pgn.PgnStr
import sttp.model.Uri
import zio.*
import zio.mock.*

import java.time.Instant

trait Mocks:
  object BoardValidatorMock extends Mock[BoardValidator]:
    object Validate extends Effect[SearchFen, BrokenComputation, ProbabilisticBoard]

    val compose: URLayer[Proxy, BoardValidator] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new BoardValidator:
          override def validate(board: SearchFen): Computation[ProbabilisticBoard] = proxy(Validate, board)
      }

  object SearchFacadeAdapterMock extends Mock[SearchFacadeAdapter]:
    object Find extends Effect[(PgnStr, ProbabilisticBoard), BrokenComputation, Boolean]

    val compose: URLayer[Proxy, SearchFacadeAdapter] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new SearchFacadeAdapter:
          override def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): Computation[Boolean] =
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

          override def profile(userName: UserName): Call[Profile]   = proxy(ProfileMethod, userName)
          override def archives(userName: UserName): Call[Archives] = proxy(ArchivesMethod, userName)
          override def games(uri: Uri): Call[Games]                 = proxy(GamesMethod, uri)
      }

  object BoardFinderMock extends Mock[BoardFinder]:
    object Find extends Effect[(SearchFen, UserId, SearchRequestId), BrokenComputation, Unit]

    val compose: URLayer[Proxy, BoardFinder] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new BoardFinder:

          override def find(
              board: SearchFen,
              userId: UserId,
              searchRequestId: SearchRequestId
          ): Computation[Unit] =
            proxy(Find, board, userId, searchRequestId)

      }

  object UserFetcherMock extends Mock[UserFetcher]:
    object GetUser  extends Effect[User, BrokenComputation, UserIdentified]
    object SaveUser extends Effect[UserIdentified, BrokenComputation, Unit]

    val compose: URLayer[Proxy, UserFetcher] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new UserFetcher:
          override def get(user: User): Computation[UserIdentified] =
            proxy(GetUser, user)
      }

  object UserRegisterMock extends Mock[UserRegister]:

    object SaveUser extends Effect[UserIdentified, BrokenComputation, Unit]

    val compose: URLayer[Proxy, UserRegister] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new UserRegister:

          override def save(user: UserIdentified): Computation[Unit] =
            proxy(SaveUser, user)
      }

  object GameSaverMock extends Mock[GameSaver]:
    object SaveGames extends Effect[(UserId, Seq[HistoricalGame]), BrokenComputation, Unit]

    val compose: URLayer[Proxy, GameSaver] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          override def save(userId: UserId, games: Seq[HistoricalGame]): Computation[Unit] =
            proxy(SaveGames, userId, games)

      }

  object GameFetcherMock extends Mock[GameFetcher]:

    object ListGames extends Effect[UserId, BrokenComputation, Seq[HistoricalGame]]

    val compose: URLayer[Proxy, GameFetcher] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          override def list(user: UserId): Computation[Seq[HistoricalGame]] =
            proxy(ListGames, user)

      }

  object SearchResultRepoMock extends Mock[SearchResultRepo]:
    object GetMethod      extends Effect[SearchRequestId, BrokenComputation, SearchResult]
    object UpdateMethod   extends Effect[SearchResult, BrokenComputation, Unit]
    object InitiateMethod extends Effect[(SearchRequestId, Instant, Int), BrokenComputation, SearchResult]

    val compose: URLayer[Proxy, SearchResultRepo] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          override def initiate(
              id: SearchRequestId,
              startSearchAt: Instant,
              total: Int
          ): Computation[SearchResult] = proxy(InitiateMethod, id, startSearchAt, total)

          override def get(searchRequestId: SearchRequestId): Computation[SearchResult] =
            proxy(GetMethod, searchRequestId)

          override def update(searchResult: SearchResult): Computation[Unit] =
            proxy(UpdateMethod, searchResult)

      }

  object TaskRepoMock extends Mock[TaskRepo]:
    object InitiateTask     extends Effect[(TaskId, Int), BrokenComputation, DownloadStatusResponse]
    object GetTask          extends Effect[TaskId, BrokenComputation, DownloadStatusResponse]
    object SuccessIncrement extends Effect[TaskId, BrokenComputation, Unit]
    object FailureIncrement extends Effect[TaskId, BrokenComputation, Unit]

    val compose: URLayer[Proxy, TaskRepo] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          override def initiate(taskId: TaskId, total: Int): Computation[DownloadStatusResponse] =
            proxy(InitiateTask, taskId, total)

          override def get(taskId: TaskId): Computation[DownloadStatusResponse] =
            proxy(GetTask, taskId)

          override def successIncrement(taskId: TaskId): Computation[Unit] =
            proxy(SuccessIncrement, taskId)

          override def failureIncrement(taskId: TaskId): Computation[Unit] =
            proxy(FailureIncrement, taskId)

      }

  object ArchiveRepoMock extends Mock[ArchiveRepo]:
    object GetMethod      extends Effect[(UserId, ArchiveId), BrokenComputation, ArchiveResult]
    object GetAllMethod   extends Effect[UserId, BrokenComputation, Seq[ArchiveResult]]
    object InitiateMethod extends Effect[(UserId, Seq[Uri]), BrokenComputation, Unit]
    object UpdateMethod   extends Effect[ArchiveResult, BrokenComputation, Unit]

    val compose: URLayer[Proxy, ArchiveRepo] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          override def initiate(
              userId: UserId,
              resources: Seq[Uri]
          ): Computation[Unit] =
            proxy(InitiateMethod, userId, resources)

          override def get(userId: UserId, archiveId: ArchiveId): Computation[ArchiveResult] =
            proxy(GetMethod, userId, archiveId)

          override def getAll(userId: UserId): Computation[Seq[ArchiveResult]] =
            proxy(GetAllMethod, userId)

          override def update(archiveResult: ArchiveResult): Computation[Unit] =
            proxy(UpdateMethod, archiveResult)
      }

  object GameDownloadingProducerMock extends Mock[DownloadGameCommandPublisher]:

    object PublishMethod extends Effect[(UserIdentified, Seq[ArchiveId], TaskId), BrokenComputation, Unit]

    val compose: URLayer[Proxy, DownloadGameCommandPublisher] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:
          override def publish(
              user: UserIdentified,
              archives: Seq[ArchiveId],
              taskId: TaskId
          ): Computation[Unit] =
            proxy(PublishMethod, user, archives, taskId)
      }

  object BoardSearchingProducerMock extends Mock[SearchBoardCommandPublisher]:

    object PublishMethod extends Effect[(UserIdentified, SearchFen, SearchRequestId), BrokenComputation, Unit]

    val compose: URLayer[Proxy, SearchBoardCommandPublisher] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:
          override def publish(
              user: UserIdentified,
              board: SearchFen,
              searchRequestId: SearchRequestId
          ): Computation[Unit] =
            proxy(PublishMethod, user, board, searchRequestId)
      }

  object SearchRequestAcceptorMock extends Mock[SearchRequestRegister]:

    object RegisterMethod
        extends Effect[(SearchFen, ChessPlatform, UserName), BrokenComputation, SearchResult]
    object CheckMethod extends Effect[SearchRequestId, BrokenComputation, SearchResult]

    val compose: URLayer[Proxy, SearchRequestRegister] =
      ZLayer {
        for proxy <- ZIO.service[Proxy]
        yield new:

          def register(
              board: SearchFen,
              platform: ChessPlatform,
              userName: UserName
          ): Computation[SearchResult] =
            proxy(RegisterMethod, board, platform, userName)

          def check(searchId: SearchRequestId): Computation[SearchResult] =
            proxy(CheckMethod, searchId)

      }
