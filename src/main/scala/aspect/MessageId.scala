package chessfinder
package aspect

import zio.*

object MessageId:
  def log(
      messageId: String
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new:
      override def apply[R, E, A](eff: ZIO[R, E, A])(implicit
          trace: Trace
      ): ZIO[R, E, A] = ZIO.logAnnotate("messageId", messageId)(eff)
