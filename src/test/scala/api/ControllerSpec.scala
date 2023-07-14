package chessfinder
package api

import client.*
import client.ClientError.*
import client.ClientExt.*
import client.chess_com.dto.*
import core.{ ProbabilisticBoard, SearchFen }
import search.BrokenLogic.*
import search.entity.*
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import testkit.parser.JsonReader
import testkit.wiremock.ClientBackdoor

import chess.format.pgn.PgnStr
import io.circe.*
import zio.*
import api.SearchResponse

import zio.http.*
import chessfinder.api.Controller
import client.chess_com.ChessDotComClient
import search.{ BoardValidator, Searcher }
import sttp.apispec.openapi.Server as OAServer
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.redoc.*
import sttp.tapir.server.*
import sttp.tapir.swagger.*

import com.typesafe.config.ConfigFactory
import zio.http.{ Client, * }
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
