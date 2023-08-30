package chessfinder
package api

import download.details.DownloadController
import search.*
import search.details.SearchController
import util.EndpointCombiner

import sttp.tapir.ztapir.*
import sttp.tapir.{ stringBody, Endpoint }
import zio.*

class Controller(val version: String) extends ZTapir:

  private val baseUrl = endpoint.in("api" / version)

  private val downloadControllerBluePrint = DownloadController(version)

  private val searchControllerBluePrint = SearchController(version)

  val `GET /api/version` =
    baseUrl.get
      .out(stringBody)

  lazy val endpoints: List[Endpoint[?, ?, ?, ?, ?]] =
    downloadControllerBluePrint.endpoints ++ searchControllerBluePrint.endpoints ++
      List(
        `GET /api/version`
      )

object Controller:

  class Impl(blueprint: Controller) extends ZTapir:

    private val `GET /api/version`: ZServerEndpoint[Unit, Any] =
      blueprint.`GET /api/version`.zServerLogic(_ => ZIO.succeed(ChessfinderBuildInfo.toString))

    private val downloadController = DownloadController.Impl(blueprint.downloadControllerBluePrint)
    private val searchController   = SearchController.Impl(blueprint.searchControllerBluePrint)

    def rest =
      EndpointCombiner.many(
        searchController.rest,
        EndpointCombiner.many(
          downloadController.rest,
          `GET /api/version` :: Nil
        )
      )
