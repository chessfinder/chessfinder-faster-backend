package chessfinder
package api

import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future
import sttp.tapir.json.circe.*
import sttp.tapir.stringBody
import sttp.tapir.ztapir.*
import search.GameFinder
import search.entity.*
import zio.*
import core.SearchFen

class ControllerBlueprint(val version: String) extends ZTapir:

  private val baseUrl = endpoint.in("api" / version)
  val `GET /api/version/game` =
    baseUrl
      .in("game")
      .in(jsonBody[FindRequest])
      .out(jsonBody[FindResponse])
      .errorOut(jsonBody[ApiError])

  val `GET /api/version` =
    baseUrl
      .out(stringBody)

  lazy val endpoints: List[Endpoint[?, ?, ?, ?, ?]] = List(`GET /api/version/game`, `GET /api/version`)

class DependentController(blueprint: ControllerBlueprint) extends ZTapir:

  val `GET /api/version/game`: ZServerEndpoint[GameFinder, Any] =
    def logic(request: FindRequest) =
      val board                   = SearchFen(request.board)
      val platform: ChessPlatform = request.platform.toPlatform
      val userName: UserName      = UserName(request.user)
      GameFinder
        .find(board, platform, userName)
        .mapBoth(
          ApiError.fromBrokenLogic,
          FindResponse.fromSearchResult
        )

    blueprint.`GET /api/version/game`.zServerLogic(logic)

  val `GET /api/version`: ZServerEndpoint[GameFinder, Any] =
    blueprint.`GET /api/version`.zServerLogic(_ => ZIO.succeed(buildinfo.BuildInfo.toString))

  def rest = List(`GET /api/version/game`, `GET /api/version`)
