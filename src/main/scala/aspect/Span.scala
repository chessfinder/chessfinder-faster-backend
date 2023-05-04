package chessfinder
package aspect

import zio.*

object Span:
  def log(
      label: String
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R, E, A](zio: ZIO[R, E, A])(implicit
          trace: Trace
      ): ZIO[R, E, A] =
        val span = ZIO.logSpan(label)(zio)
        span.tap(_ => ZIO.logInfo("I am here"))
    }
