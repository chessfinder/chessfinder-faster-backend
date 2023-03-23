package chessfinder
package api 

import zio.test.*
import zio.*
import client.chess_com.ChessDotComClient
import chessfinder.testkit.wiremock.ClientBackdoor
import sttp.model.Uri
import client.chess_com.dto.*
import client.*
import client.ClientError.*
import search.entity.UserName
import scala.util.Success
import zio.http.Client
import sttp.model.Uri.UriContext
import zio.http.service.{ ChannelFactory, EventLoopGroup }
import zio.*
import zio.http.Body
import client.ClientExt.*
import chessfinder.api.FindResponse
import api.FindResponse
import zio.http.Client
import io.circe.*
import io.circe.parser
import scala.io.Source
import testkit.parser.JsonReader
import zio.test.*
import chessfinder.core.SearchFen
import chessfinder.core.ProbabilisticBoard
import search.BrokenLogic.*
import search.entity.*
import sttp.model.Uri.UriContext
import client.chess_com.dto.*
import chess.format.pgn.PgnStr
import zio.mock.Expectation
import zio.ZIOApp
import zio.ZIOAppDefault

import sttp.tapir.ztapir.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.http.{ HttpApp, Request, Response }
import zio.*
import zio.http.*
import chessfinder.api.Controller
import chessfinder.search.GameFinder
import zio.Console.ConsoleLive
import sttp.apispec.openapi.Server as OAServer
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.swagger.*
import sttp.tapir.redoc.*
import sttp.tapir.redoc.RedocUIOptions
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.server.*
import chessfinder.search.BoardValidator
import chessfinder.search.GameDownloader
import chessfinder.search.Searcher
import chessfinder.client.chess_com.ChessDotComClient
import com.typesafe.config.ConfigFactory

object ControllerSpec extends Mocks:

  val version    = "newborn"
  val controller = Controller(version)

  private val config = ConfigFactory.load()
  private val configLayer = ZLayer.succeed(config)

  private val zioInterpreter = ZioHttpInterpreter()

  private val endpoints =
    controller.rest

  val app =
    zioInterpreter.toHttp(endpoints).withDefaultErrorResponse

  protected lazy val clientLayer = Client.default.orDie

  
  def run(controllerLayer: ULayer[GameFinder]) =
    Server
      .serve(app)
      .provide(
        Server.default,
        controllerLayer,
      )

  // don't know how to right a test for the controller.
  
