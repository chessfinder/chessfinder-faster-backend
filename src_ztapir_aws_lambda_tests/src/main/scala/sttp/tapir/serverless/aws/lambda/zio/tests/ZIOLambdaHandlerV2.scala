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

given RIOMonadError[Any] = RIOMonadError[Any]

class ZIOLambdaHandlerV2 extends ZLambdaHandler[Any, AwsRequest]:

  override protected def getAllEndpoints: List[ZServerEndpoint[Any, Any]] = all.allEndpoints.toList

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    val runtime = Runtime.default
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(process(input, output)).getOrThrowFiberFailure()
    }
