package chessfinder
package api

import api.{ Controller, SearchResponse }
import client.*
import client.ClientError.*
import client.ClientExt.*
import client.chess_com.ChessDotComClient
import client.chess_com.dto.*
import core.{ ProbabilisticBoard, SearchFen }
import search.BrokenLogic.*
import search.entity.*
import search.{ BoardValidator, Searcher }
import testkit.parser.JsonReader
import testkit.wiremock.ClientBackdoor

import chess.format.pgn.PgnStr
import com.typesafe.config.ConfigFactory
import io.circe.*
import sttp.apispec.openapi.Server as OAServer
import sttp.apispec.openapi.circe.yaml.*
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.redoc.*
import sttp.tapir.server.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.*
import sttp.tapir.ztapir.*
import zio.*
import zio.http.*
import zio.mock.Expectation
import zio.test.*

import scala.io.Source
import scala.util.Success

object ControllerSpec extends Mocks:

  val version    = "async"
  val blueprint  = Controller(version)
  val controller = Controller.Impl(blueprint)

  private val config      = ConfigFactory.load()
  private val configLayer = ZLayer.succeed(config)

  private val zioInterpreter = ZioHttpInterpreter()

  private val endpoints =
    controller.rest

  val app =
    zioInterpreter.toHttp(endpoints).withDefaultErrorResponse

  protected lazy val clientLayer = Client.default.orDie

  // def run(controllerLayer: ULayer[GameFinder]) =
  //   Server
  //     .serve(app)
  //     .provide(
  //       Server.default,
  //       controllerLayer
  //     )

  // don't know how to write a test for the controller.
