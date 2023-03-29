package sttp.tapir.serverless.aws.lambda.zio

import sttp.tapir.server.interceptor.CustomiseInterceptors
import zio.RIO
import sttp.tapir.serverless.aws.lambda.AwsServerOptions

object AwsZServerOptions:

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors[R]: CustomiseInterceptors[RIO[R, *], AwsServerOptions[RIO[R, *]]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[RIO[R, *], AwsServerOptions[RIO[R, *]]]) => AwsServerOptions(encodeResponseBody = true, ci.interceptors)
    )

  def default[R]: AwsServerOptions[RIO[R, *]] = customiseInterceptors.options

  def noEncoding[R]: AwsServerOptions[RIO[R, *]] =
    this.default[R].copy(encodeResponseBody = false)
