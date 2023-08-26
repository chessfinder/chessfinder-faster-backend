package chessfinder

import api.Controller
import client.ZLoggingAspect
import client.chess_com.ChessDotComClient
import persistence.GameRecord
import persistence.core.DefaultDynamoDBExecutor
import pubsub.core.DefaultSqsExecutor
import search.*
import search.repo.{ GameRepo, TaskRepo, UserRepo }
import util.EndpointCombiner

import com.typesafe.config.ConfigFactory
import sttp.apispec.openapi.Server as OAServer
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.redoc.*
import sttp.tapir.server.*
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.ziohttp.*
import sttp.tapir.swagger.*
import sttp.tapir.ztapir.*
import zio.aws.core.config.AwsConfig
import zio.aws.netty
import zio.aws.sqs.Sqs
import zio.config.typesafe.TypesafeConfigProvider
import zio.dynamodb.*
import zio.http.{ Client, HttpApp, Request, Response }
import zio.logging.*
import zio.{ ZIOApp, ZIOAppDefault, * }

trait ConfigModule:
  // protected val configLayer = Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())
  protected def configLayer: ZLayer[Any, Nothing, Unit]
  // protected val loggingLayer = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
  protected val loggingLayer = Runtime.removeDefaultLoggers >>> zio.logging.consoleJsonLogger()

trait ClientModule extends ConfigModule:
  protected lazy val clientLayer =
    Client.default.map(z => z.update(_ @@ ZLoggingAspect())).orDie

trait DynamoDbModule extends ClientModule:
  protected lazy val dynamodbLayer: TaskLayer[DynamoDBExecutor] =
    val in = ((netty.NettyHttpClient.default >+> AwsConfig.default) ++ configLayer)
    in >>> DefaultDynamoDBExecutor.layer

trait SqsModule extends ClientModule:

  protected lazy val sqsLayer: TaskLayer[Sqs] =
    val in = ((netty.NettyHttpClient.default >+> AwsConfig.default) ++ configLayer)
    in >>> DefaultSqsExecutor.layer

trait BaseMain extends DynamoDbModule with SqsModule with ClientModule:

  val organization = "eudemonia"

  protected val controllerBlueprint = Controller("async")
  protected val controller          = Controller.Impl(controllerBlueprint)

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
