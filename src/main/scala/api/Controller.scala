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
import core.format.SearchFen

class Controller(version: String) extends ZTapir:

  private val baseUrl = endpoint.in("api" / version)

  val `GET /game` =
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

    baseUrl
      .in(jsonBody[FindRequest])
      .out(jsonBody[FindResponse])
      .errorOut(jsonBody[ApiError])
      .zServerLogic(logic)

  def rest = List(`GET /game`)

  lazy val endpoints: List[Endpoint[_, _, _, _, _]] = rest.map(_.endpoint)

