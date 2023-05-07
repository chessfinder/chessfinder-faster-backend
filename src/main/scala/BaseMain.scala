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
import zio.logging.backend.SLF4J
import util.EndpointCombiner
import chessfinder.search.TaskStatusChecker
import chessfinder.persistence.GameRecord
import chessfinder.search.GameDownloader
import sttp.tapir.server.ziohttp.*
import zio.logging.*
import zio.config.typesafe.TypesafeConfigProvider
import chessfinder.client.ZLoggingAspect
import sttp.tapir.server.interceptor.log.DefaultServerLog

abstract class BaseMain:

  val organization = "eudemonia"

  // protected val configLayer = Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())
  protected def configLayer: ZLayer[Any, Nothing, Unit]
  // protected val loggingLayer = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
  protected val loggingLayer = Runtime.removeDefaultLoggers >>> zio.logging.consoleJsonLogger()

  protected lazy val dynamodbLayer: TaskLayer[DynamoDBExecutor] =
    val in = ((netty.NettyHttpClient.default >+> AwsConfig.default) ++ configLayer)
    in >>> DefaultDynamoDBExecutor.layer

  protected lazy val clientLayer =
    Client.default.map(z => z.update(_ @@ ZLoggingAspect())).orDie

  protected val syncControllerBlueprint = SyncController("newborn")
  protected val syncController          = SyncController.Impl(syncControllerBlueprint)

  protected val asyncControllerBlueprint = AsyncController("async")
  protected val asyncController          = AsyncController.Impl(asyncControllerBlueprint)

  def serverLogger[R]: DefaultServerLog[RIO[R, *]] = ZioHttpServerOptions.defaultServerLog
    .copy(
      doLogWhenReceived = msg => ZIO.logInfo(msg),
      doLogWhenHandled = (msg: String, exOpt: Option[Throwable]) =>
        ZIO.logInfoCause(msg, exOpt.map(e => Cause.fail(e)).getOrElse(Cause.empty)),
      doLogAllDecodeFailures = (msg: String, exOpt: Option[Throwable]) =>
        ZIO.logInfoCause(msg, exOpt.map(e => Cause.fail(e)).getOrElse(Cause.empty)),
      doLogExceptions = (msg: String, ex: Throwable) => ZIO.logErrorCause(msg, Cause.fail(ex)),
      noLog = ZIO.unit,
      logWhenReceived = true,
      logAllDecodeFailures = true
    )
