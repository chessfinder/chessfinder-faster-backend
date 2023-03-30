package chessfinder
package api

import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future
import sttp.tapir.json.circe.*
import sttp.tapir.ztapir.*
import search.GameFinder
import search.entity.*
import zio.*
import core.SearchFen


class ControllerBlueprint(version: String) extends ZTapir:

  private val baseUrl = endpoint.in("api" / version)
  val `GET /game` =
    baseUrl
      .in("game")
      .in(jsonBody[FindRequest])
      .out(jsonBody[FindResponse])
      .errorOut(jsonBody[ApiError])
  
  lazy val endpoints: List[Endpoint[?, ?, ?, ?, ?]] = List(`GET /game`)
  

class PureController(blueprint: ControllerBlueprint, gameFinder: GameFinder) extends ZTapir:

  val `GET /game`: ZServerEndpoint[Any, Any] =
    def logic(request: FindRequest) =
      val board                   = SearchFen(request.board)
      val platform: ChessPlatform = request.platform.toPlatform
      val userName: UserName      = UserName(request.user)
      gameFinder
        .find(board, platform, userName)
        .mapBoth(
          ApiError.fromBrokenLogic,
          FindResponse.fromSearchResult
        )

    blueprint.`GET /game`.zServerLogic(logic)
  
  def rest = List(`GET /game`)

class DependentController(blueprint: ControllerBlueprint) extends ZTapir:

  val `GET /game`: ZServerEndpoint[GameFinder, Any] =
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

    blueprint.`GET /game`.zServerLogic(logic)

  def rest = List(`GET /game`)
