package chessfinder
package app

import api.Controller
import client.ZLoggingAspect
import persistence.core.DefaultDynamoDBExecutor
import pubsub.core.DefaultSqsExecutor

import sttp.tapir.server.*
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.ziohttp.*
import zio.*
import zio.aws.core.config.AwsConfig
import zio.aws.netty
import zio.aws.sqs.Sqs
import zio.dynamodb.*
import zio.http.{ Client, Response }
import zio.logging.*

trait ConfigModule:
  protected def configLayer: ZLayer[Any, Nothing, Unit]
  protected val loggingLayer: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> zio.logging.consoleJsonLogger()

trait ClientModule extends ConfigModule:
  protected lazy val clientLayer: ZLayer[Any, Nothing, Client] =
    Client.default.map(z => z.update(_ @@ ZLoggingAspect())).orDie

trait DynamoDbModule extends ClientModule:
  protected lazy val dynamodbLayer: TaskLayer[DynamoDBExecutor] =
    val in = (netty.NettyHttpClient.default >+> AwsConfig.default) ++ configLayer
    in >>> DefaultDynamoDBExecutor.layer

trait SqsModule extends ClientModule:

  protected lazy val sqsLayer: TaskLayer[Sqs] =
    val in = (netty.NettyHttpClient.default >+> AwsConfig.default) ++ configLayer
    in >>> DefaultSqsExecutor.layer

trait MainModule extends DynamoDbModule with SqsModule with ClientModule:

  val organization = "eudemonia"

  private val controllerBlueprint = Controller("structured")
  protected val controller        = Controller.Impl(controllerBlueprint)

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
