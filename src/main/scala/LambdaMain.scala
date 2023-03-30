package chessfinder

import zio.ZIOApp
import zio.ZIOAppDefault

import sttp.tapir.ztapir.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.http.{ HttpApp, Request, Response }
import zio.*
import zio.http.{ App as _, * }
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
import sttp.tapir.serverless.aws.lambda.LambdaHandler

import cats.effect.unsafe.implicits.global
import com.amazonaws.services.lambda.runtime.Context
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda.{ AwsRequest, LambdaHandler }
import java.io.{ InputStream, OutputStream }
import cats.implicits.*
import zio.interop.catz.*
import sttp.tapir.serverless.aws.lambda.zio.ZLambdaHandler
import zio.Task
import zio.{ Task, ZIO }
import cats.effect.unsafe.implicits.global
import com.amazonaws.services.lambda.runtime.Context
import io.circe.generic.auto.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda.{ AwsRequest, LambdaHandler }
import java.io.{ InputStream, OutputStream }
import sttp.tapir.serverless.aws.lambda.zio.ZLambdaHandler
import sttp.tapir.ztapir.ZServerEndpoint
import sttp.tapir.ztapir.RIOMonadError
import zio.{ Runtime, Unsafe }
import chessfinder.api.{ ControllerBlueprint, DependentController }
import com.amazonaws.services.lambda.runtime.RequestStreamHandler

object LambdaMain extends RequestStreamHandler:

  val organization = "eudemonia"
  val version      = "newborn"

  val blueprint  = ControllerBlueprint(version)
  val controller = DependentController(blueprint)

  val handler = ZLambdaHandler.withMonadError(controller.rest)

  private val config      = ConfigFactory.load()
  private val configLayer = ZLayer.succeed(config)

  private lazy val clientLayer = Client.default.orDie

  def process(input: InputStream, output: OutputStream) =
    handler
      .process[AwsRequest](input, output)
      .provide(
        configLayer,
        clientLayer,
        BoardValidator.Impl.layer,
        GameFinder.Impl.layer,
        Searcher.Impl.layer,
        GameDownloader.Impl.layer,
        ChessDotComClient.Impl.layer
      )

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    val runtime = Runtime.default
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(process(input, output)).getOrThrowFiberFailure()
    }
