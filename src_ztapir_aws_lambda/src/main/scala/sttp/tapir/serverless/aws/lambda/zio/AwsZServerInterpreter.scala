package sttp.tapir.serverless.aws.lambda.zio

import sttp.tapir.serverless.aws.lambda.AwsServerInterpreter
import sttp.model.{HeaderNames, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax.*
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import zio.RIO
import sttp.tapir.ztapir.RIOMonadError
import sttp.tapir.serverless.aws.lambda.AwsServerOptions

private[lambda] abstract class AwsZServerInterpreter[R: RIOMonadError] extends AwsServerInterpreter[RIO[R,*]]

object AwsZServerInterpreter:

  def apply[R: RIOMonadError](serverOptions: AwsServerOptions[RIO[R,*]]): AwsZServerInterpreter[R] =
    new AwsZServerInterpreter[R]:
      override def awsServerOptions: AwsServerOptions[RIO[R,*]] = serverOptions
  

  def apply[R: RIOMonadError](): AwsZServerInterpreter[R] =
    new AwsZServerInterpreter[R]:
      override def awsServerOptions: AwsServerOptions[RIO[R,*]] = AwsZServerOptions.default[R]
