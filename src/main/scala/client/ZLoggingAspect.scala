package chessfinder
package client

import zio.ZIO
import zio.http.*

class ZLoggingAspect[Env]
    extends ZClientAspect[Env, Env, Body, Body, Throwable, Throwable, Response, Response]:

  // decorator pattern is broken, we have acces only to the body, no headers, no other parts of the request, only body :(((((
  override def apply[
      E >: Env <: Env,
      In >: Body <: Body,
      Err >: Throwable <: Throwable,
      Out >: Response <: Response
  ](client: ZClient[E, Body, Throwable, Response]): ZClient[E, Body, Throwable, Response] =
    client
      .contramapZIO[E, Throwable, Body] { body =>
        for
          bodyAsString <- body.asString.orElseSucceed("Request body is not a string")
          _            <- ZIO.logInfo(s"Request body: $bodyAsString")
        yield body
      }
      .mapZIO { response =>
        for _ <- ZIO.logInfo(s"Response: ${response.status.code}")
        yield response
      }
