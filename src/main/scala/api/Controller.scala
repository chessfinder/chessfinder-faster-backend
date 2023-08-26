package chessfinder
package api

import api.TaskResponse
import aspect.Span
import core.SearchFen
import search.*
import search.entity.*
import util.EndpointCombiner

import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.ztapir.*
import sttp.tapir.{ stringBody, Endpoint }
import zio.*

import java.util.UUID
import scala.concurrent.Future

class Controller(val version: String) extends ZTapir:

  private val baseUrl = endpoint.in("api" / version)

  val `POST /api/version/game` =
    baseUrl.post
      .in("game")
      .in(jsonBody[DownloadRequest])
      .out(jsonBody[TaskResponse])
      .errorOut(jsonBody[ApiError])

  val `POST /api/version/board` =
    baseUrl.post
      .in("board")
      .in(jsonBody[SearchRequest])
      .out(jsonBody[SearchResponse])
      .errorOut(jsonBody[ApiError])

  val `GET /api/version/task` =
    baseUrl.get
      .in("task")
      .in(query[UUID]("taskId"))
      .out(jsonBody[TaskStatusResponse])
      .errorOut(jsonBody[ApiError])

  val `GET /api/version/board` =
    baseUrl.get
      .in("board")
      .in(query[UUID]("searchRequestId"))
      .out(jsonBody[SearchResultResponse])
      .errorOut(jsonBody[ApiError])

  val `GET /api/version` =
    baseUrl.get
      .out(stringBody)

  lazy val endpoints: List[Endpoint[?, ?, ?, ?, ?]] =
    List(
      `POST /api/version/game`,
      `POST /api/version/board`,
      `GET /api/version/task`,
      `GET /api/version/board`,
      `GET /api/version`
    )

object Controller:

  class Impl(blueprint: Controller) extends ZTapir:

    val `POST /api/version/game`: ZServerEndpoint[ArchiveDownloader, Any] =
      def logic(request: DownloadRequest): zio.ZIO[ArchiveDownloader, ApiError, TaskResponse] =
        val platform: ChessPlatform = request.platform.toPlatform
        val userName: UserName      = UserName(request.user)
        val user: User              = User(platform, userName)
        ZIO
          .serviceWithZIO[ArchiveDownloader](_.cache(user))
          .mapBoth(
            ApiError.fromBrokenLogic,
            taskId => TaskResponse(taskId.value)
          )

      blueprint.`POST /api/version/game`.zServerLogic(logic)

    val `GET /api/version/board`: ZServerEndpoint[SearchRequestAcceptor, Any] =
      def logic(searchRequestId: UUID): zio.ZIO[SearchRequestAcceptor, ApiError, SearchResultResponse] =
        ZIO
          .serviceWithZIO[SearchRequestAcceptor](_.check(SearchRequestId(searchRequestId)))
          .mapBoth(
            ApiError.fromBrokenLogic,
            SearchResultResponse.fromSearchResult
          )

      blueprint.`GET /api/version/board`.zServerLogic(logic)

    val `POST /api/version/board`: ZServerEndpoint[SearchRequestAcceptor, Any] =

      def logic(request: SearchRequest): zio.ZIO[SearchRequestAcceptor, ApiError, SearchResponse] =
        val board                   = SearchFen(request.board)
        val platform: ChessPlatform = request.platform.toPlatform
        val userName: UserName      = UserName(request.user)
        ZIO
          .serviceWithZIO[SearchRequestAcceptor](_.register(board, platform, userName))
          .mapBoth(
            ApiError.fromBrokenLogic,
            SearchResponse.fromSearchResult
          )

      blueprint.`POST /api/version/board`.zServerLogic(logic)

    val `GET /api/version/task`: ZServerEndpoint[TaskStatusChecker, Any] =
      def logic(taskId: UUID): zio.ZIO[TaskStatusChecker, ApiError, TaskStatusResponse] =
        ZIO
          .serviceWithZIO[TaskStatusChecker](_.check(TaskId(taskId)))
          .mapError(ApiError.fromBrokenLogic)
      blueprint.`GET /api/version/task`.zServerLogic(logic)

    val `GET /api/version`: ZServerEndpoint[TaskStatusChecker, Any] =
      blueprint.`GET /api/version`.zServerLogic(_ => ZIO.succeed(ChessfinderBuildInfo.toString))

    def rest =
      EndpointCombiner(
        `POST /api/version/game`,
        EndpointCombiner(
          `POST /api/version/board`,
          EndpointCombiner(
            `GET /api/version/task`,
            EndpointCombiner(`GET /api/version/board`, `GET /api/version` :: Nil)
          )
        )
      )
