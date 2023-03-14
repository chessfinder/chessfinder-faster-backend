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

class Controller(version: String)(service: GameFinder) extends ZTapir:

  type PublicHttpEndpoint[I, O] = Endpoint[Unit, I, ApiError, O, Any]
  type UnknownEndpoint          = Endpoint[_, _, _, _, _]

  private val baseUrl = endpoint.in("api" / version)

  private val `GET /game` =
    def logic(request: FindRequest): ZIO[Any, ApiError, FindResponse] =
      val board                   = SearchFen(request.board)
      val platform: ChessPlatform = request.platform.toPlatform
      val userName: UserName      = UserName(request.user)
      service
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

  def rest: List[ZServerEndpoint[Nothing, Any]] = List(`GET /game`)

  lazy val endpoints: List[Endpoint[_, _, _, _, _]] = rest.map(_.endpoint)
