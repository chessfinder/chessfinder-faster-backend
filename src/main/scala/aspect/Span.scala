package chessfinder
package aspect

import zio.*

object Span:
  def log(
      label: String
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R, E, A](eff: ZIO[R, E, A])(implicit
          trace: Trace
      ): ZIO[R, E, A] =
        val span = ZIO.logSpan(label) {
          for
            res <- eff
            _   <- ZIO.logInfo(s"Method summary")
          yield res
        }
        span
    }

  val log: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    Span.log("Span")
