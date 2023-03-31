package sttp.tapir.serverless.aws.lambda.zio

import sttp.tapir.server.interceptor.CustomiseInterceptors
import zio.RIO
import sttp.tapir.serverless.aws.lambda.AwsServerOptions
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.{ CustomiseInterceptors, Interceptor }
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.{ Defaults, TapirFile }
import zio.{ Cause, RIO, Task, ZIO }

object AwsZServerOptions:

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors[R]: CustomiseInterceptors[RIO[R, *], AwsServerOptions[RIO[R, *]]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[RIO[R, *], AwsServerOptions[RIO[R, *]]]) =>
        AwsServerOptions(encodeResponseBody = true, ci.interceptors)
    )

  def default[R]: AwsServerOptions[RIO[R, *]] = customiseInterceptors.options

  def noEncoding[R]: AwsServerOptions[RIO[R, *]] =
    this.default[R].copy(encodeResponseBody = false)

  def noEncoding[R](options: AwsServerOptions[RIO[R, *]]): AwsServerOptions[RIO[R, *]] =
    options.copy(encodeResponseBody = false)
