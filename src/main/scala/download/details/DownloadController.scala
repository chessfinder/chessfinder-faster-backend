package chessfinder
package download.details

import api.ApiError
import download.{ ArchiveDownloader, DownloadStatusChecker }
import util.EndpointCombiner

import sttp.tapir.Endpoint
import sttp.tapir.json.circe.*
import sttp.tapir.ztapir.*
import zio.*

import java.util.UUID

class DownloadController(val version: String) extends ZTapir:

  private val baseUrl = endpoint.in("api" / version)

  val `POST /api/version/game` =
    baseUrl.post
      .in("game")
      .in(jsonBody[DownloadRequest])
      .out(jsonBody[DownloadResponse])
      .errorOut(jsonBody[ApiError])

  val `GET /api/version/game` =
    baseUrl.get
      .in("game")
      .in(query[UUID]("downloadRequestId"))
      .out(jsonBody[DownloadStatusResponse])
      .errorOut(jsonBody[ApiError])

  lazy val endpoints: List[Endpoint[?, ?, ?, ?, ?]] =
    List(
      `POST /api/version/game`,
      `GET /api/version/game`
    )

object DownloadController:

  class Impl(blueprint: DownloadController) extends ZTapir:

    private val `POST /api/version/game`: ZServerEndpoint[ArchiveDownloader, Any] =
      def logic(request: DownloadRequest): zio.ZIO[ArchiveDownloader, ApiError, DownloadResponse] =
        val platform: ChessPlatform = request.platform.toPlatform
        val userName: UserName      = UserName(request.user)
        val user: User              = chessfinder.User(platform, userName)
        ZIO
          .serviceWithZIO[ArchiveDownloader](_.cache(user))
          .mapBoth(
            ApiError.fromBrokenLogic,
            taskId => DownloadResponse(taskId.value)
          )

      blueprint.`POST /api/version/game`.zServerLogic(logic)

    private val `GET /api/version/game`: ZServerEndpoint[DownloadStatusChecker, Any] =
      def logic(taskId: UUID): zio.ZIO[DownloadStatusChecker, ApiError, DownloadStatusResponse] =
        ZIO
          .serviceWithZIO[DownloadStatusChecker](_.check(TaskId(taskId)))
          .mapError(ApiError.fromBrokenLogic)
      blueprint.`GET /api/version/game`.zServerLogic(logic)

    def rest =
      EndpointCombiner(
        `POST /api/version/game`,
        `GET /api/version/game` :: Nil
      )
