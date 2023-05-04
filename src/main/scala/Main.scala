package chessfinder

import zio.ZIOApp
import zio.ZIOAppDefault

import sttp.tapir.ztapir.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.http.{ HttpApp, Request, Response }
import zio.*
import zio.http.*
import chessfinder.api.{ AsyncController, SyncController }
import chessfinder.search.GameFinder
import sttp.apispec.openapi.Server as OAServer
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.swagger.*
import sttp.tapir.redoc.*
import sttp.tapir.redoc.RedocUIOptions
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.server.*
import chessfinder.search.BoardValidator
import chessfinder.search.GameFetcher
import chessfinder.search.Searcher
import chessfinder.client.chess_com.ChessDotComClient
import com.typesafe.config.ConfigFactory
import chessfinder.api.ApiVersion
import chessfinder.search.repo.{ GameRepo, TaskRepo, UserRepo }
import zio.aws.netty
import zio.aws.core.config.AwsConfig
import persistence.core.DefaultDynamoDBExecutor
import zio.dynamodb.*
import util.EndpointCombiner
import chessfinder.search.TaskStatusChecker
import chessfinder.persistence.GameRecord
import chessfinder.search.GameDownloader
import sttp.tapir.server.ziohttp.*
import zio.logging.*
import zio.config.typesafe.TypesafeConfigProvider

object Main extends BaseMain with ZIOAppDefault:

  private val servers: List[OAServer] = List(
    OAServer("http://localhost:8080").description("Chessfinder APIs")
  )
  private val docsAsYaml: String = OpenAPIDocsInterpreter()
    .toOpenAPI(
      syncControllerBlueprint.endpoints ++ asyncControllerBlueprint.endpoints,
      "ChessFinder",
      "Backend"
    )
    .servers(servers)
    .toYaml

  private val zioInterpreter =
    ZioHttpInterpreter[Any](
      ZioHttpServerOptions.customiseInterceptors
        .serverLog(serverLogger)
        .options
    )

  private val swaggerEndpoint =
    val options = SwaggerUIOptions.default.copy(pathPrefix = List("docs", "swagger"))
    SwaggerUI[zio.RIO[Any, *]](docsAsYaml, options = options)

  private val redocEndpoint =
    val options = RedocUIOptions.default.copy(pathPrefix = List("docs", "redoc"))
    Redoc[zio.RIO[Any, *]]("ChessFinder", spec = docsAsYaml, options = options)

  private val rest =
    EndpointCombiner.many(asyncController.rest, syncController.rest)

  private val endpoints =
    EndpointCombiner.many(EndpointCombiner.many(rest, swaggerEndpoint), redocEndpoint)

  val app =
    zioInterpreter.toHttp(endpoints).withDefaultErrorResponse

  override val bootstrap = configLayer >+> loggingLayer

  ZIOAspect
  def run =
    Server
      .serve(app)
      .provide(
        configLayer,
        clientLayer,
        Server.default,
        BoardValidator.Impl.layer,
        GameFinder.Impl.layer[ApiVersion.Newborn.type],
        GameFinder.Impl.layer[ApiVersion.Async.type],
        Searcher.Impl.layer,
        GameFetcher.Impl.layer,
        GameFetcher.Local.layer,
        ChessDotComClient.Impl.layer,
        UserRepo.Impl.layer,
        TaskRepo.Impl.layer,
        GameRepo.Impl.layer,
        TaskStatusChecker.Impl.layer,
        GameDownloader.Impl.layer,
        dynamodbLayer,
        ZLayer.succeed(zio.Random.RandomLive)
      )
