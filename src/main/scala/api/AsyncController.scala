package chessfinder
package api

import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future
import sttp.tapir.json.circe.*
import sttp.tapir.stringBody
import sttp.tapir.ztapir.*
import search.{ GameDownloader, GameFinder, TaskStatusChecker }
import search.entity.*
import zio.*
import core.SearchFen
import api.TaskResponse
import java.util.UUID
import api.ApiVersion
import util.EndpointCombiner

class AsyncController(val version: String) extends ZTapir:

  private val baseUrl = endpoint.in("api" / version)

  val `POST /api/version/game` =
    baseUrl.post
      .in("game")
      .in(jsonBody[DownloadRequest])
      .out(jsonBody[TaskResponse])
      .errorOut(jsonBody[ApiError])

  val `GET /api/version/board` =
    baseUrl.post
      .in("board")
      .in(jsonBody[FindRequest])
      .out(jsonBody[FindResponse])
      .errorOut(jsonBody[ApiError])

  val `GET /api/version/task` =
    baseUrl.get
      .in("task")
      .in(query[UUID]("taskId"))
      .out(jsonBody[TaskStatusResponse])
      .errorOut(jsonBody[ApiError])

  val `GET /api/version` =
    baseUrl.get
      .out(stringBody)

  lazy val endpoints: List[Endpoint[?, ?, ?, ?, ?]] =
    List(
      `POST /api/version/game`,
      `GET /api/version/board`,
      `GET /api/version/task`,
      `GET /api/version`
    )

object AsyncController:

  type V = ApiVersion.Async.type

  class Impl(blueprint: AsyncController) extends ZTapir:

    val `POST /api/version/game`: ZServerEndpoint[GameDownloader, Any] =
      def logic(request: DownloadRequest): zio.ZIO[GameDownloader, ApiError, TaskResponse] =
        val platform: ChessPlatform = request.platform.toPlatform
        val userName: UserName      = UserName(request.user)
        val user: User              = User(platform, userName)
        GameDownloader
          .cache(user)
          .mapBoth(
            ApiError.fromBrokenLogic,
            taskId => TaskResponse(taskId.value)
          )

      blueprint.`POST /api/version/game`.zServerLogic(logic)

    val `GET /api/version/board`: ZServerEndpoint[GameFinder[V], Any] =
      def logic(request: FindRequest): zio.ZIO[GameFinder[V], ApiError, FindResponse] =
        val board                   = SearchFen(request.board)
        val platform: ChessPlatform = request.platform.toPlatform
        val userName: UserName      = UserName(request.user)
        GameFinder
          .find[V](board, platform, userName)
          .mapBoth(
            ApiError.fromBrokenLogic,
            FindResponse.fromSearchResult
          )

      blueprint.`GET /api/version/board`.zServerLogic(logic)

    val `GET /api/version/task`: ZServerEndpoint[TaskStatusChecker, Any] =
      def logic(taskId: UUID): zio.ZIO[TaskStatusChecker, ApiError, TaskStatusResponse] =
        TaskStatusChecker.check(TaskId(taskId)).mapError(ApiError.fromBrokenLogic)
      blueprint.`GET /api/version/task`.zServerLogic(logic)

    val `GET /api/version`: ZServerEndpoint[GameFinder[V], Any] =
      blueprint.`GET /api/version`.zServerLogic(_ => ZIO.succeed(buildinfo.BuildInfo.toString))

    def rest =
      EndpointCombiner(
        `POST /api/version/game`,
        EndpointCombiner(
          `GET /api/version/board`,
          EndpointCombiner(`GET /api/version/task`, `GET /api/version` :: Nil)
        )
      )
