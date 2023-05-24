package chessfinder
package aspect

import zio.*

object TaskId:
  def log(
      taskId: String
  ): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new:
      override def apply[R, E, A](eff: ZIO[R, E, A])(implicit
          trace: Trace
      ): ZIO[R, E, A] = ZIO.logAnnotate("taskId", taskId)(eff)
