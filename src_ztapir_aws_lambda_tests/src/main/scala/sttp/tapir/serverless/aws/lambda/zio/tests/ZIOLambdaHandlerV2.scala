package sttp.tapir.serverless.aws.lambda.zio.tests

import cats.effect.IO
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
import zio.{ Runtime, Unsafe}
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import zio.logging.*

class ZIOLambdaHandlerV2 extends RequestStreamHandler:
  val handler = ZLambdaHandler.withMonadError[Any](all.allEndpoints.toList)
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    val runtime = Runtime.default
    Unsafe.unsafe { implicit unsafe =>
      val env = Runtime.removeDefaultLoggers >>> consoleLogger()
      runtime.unsafe.run(handler.process[AwsRequest](input, output).provide(env)).getOrThrowFiberFailure()
    }
