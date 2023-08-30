package chessfinder
package search.details

import core.SearchFen
import download.{ ArchiveDownloader, DownloadStatusChecker }
import search.*
import util.EndpointCombiner

import chessfinder.api.ApiError
import sttp.tapir.json.circe.*
import sttp.tapir.ztapir.*
import sttp.tapir.{ stringBody, Endpoint }
import zio.*

import java.util.UUID

class SearchController(val version: String) extends ZTapir:

  private val baseUrl = endpoint.in("api" / version)

  val `POST /api/version/board` =
    baseUrl.post
      .in("board")
      .in(jsonBody[SearchRequest])
      .out(jsonBody[SearchResponse])
      .errorOut(jsonBody[ApiError])

  val `GET /api/version/board` =
    baseUrl.get
      .in("board")
      .in(query[UUID]("searchRequestId"))
      .out(jsonBody[SearchStatusResponse])
      .errorOut(jsonBody[ApiError])

  lazy val endpoints: List[Endpoint[?, ?, ?, ?, ?]] =
    List(
      `POST /api/version/board`,
      `GET /api/version/board`
    )

object SearchController:

  class Impl(blueprint: SearchController) extends ZTapir:

    private val `GET /api/version/board`: ZServerEndpoint[SearchResultChecker, Any] =
      def logic(searchRequestId: UUID): zio.ZIO[SearchResultChecker, ApiError, SearchStatusResponse] =
        ZIO
          .serviceWithZIO[SearchResultChecker](_.check(SearchRequestId(searchRequestId)))
          .mapBoth(
            ApiError.fromBrokenLogic,
            SearchStatusResponse.fromSearchResult
          )

      blueprint.`GET /api/version/board`.zServerLogic(logic)

    private val `POST /api/version/board`: ZServerEndpoint[SearchRequestRegister, Any] =

      def logic(request: SearchRequest): zio.ZIO[SearchRequestRegister, ApiError, SearchResponse] =
        val board                   = SearchFen(request.board)
        val platform: ChessPlatform = request.platform.toPlatform
        val userName: UserName      = UserName(request.user)
        ZIO
          .serviceWithZIO[SearchRequestRegister](_.register(board, platform, userName))
          .mapBoth(
            ApiError.fromBrokenLogic,
            SearchResponse.fromSearchResult
          )

      blueprint.`POST /api/version/board`.zServerLogic(logic)

    def rest = EndpointCombiner(`GET /api/version/board`, `POST /api/version/board` :: Nil)
