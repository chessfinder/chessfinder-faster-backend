package chessfinder
package download.details

import aspect.Span
import persistence.{ PlatformType, UserRecord }
import search.*
import search.*

import zio.dynamodb.{ DynamoDBError, DynamoDBExecutor }
import zio.{ Cause, ZIO, ZLayer }

trait UserRegister:

  def save(user: UserIdentified): Computation[Unit]

object UserRegister:

  class Impl(executor: DynamoDBExecutor) extends UserRegister:
    private val layer = ZLayer.succeed(executor)

    override def save(user: UserIdentified): Computation[Unit] =
      val eff = UserRecord.Table
        .put(UserRecord.fromUserIdentified(user))
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .mapError(_ => BrokenComputation.ServiceOverloaded)
      eff @@ Span.log

  object Impl:
    val layer = ZLayer {
      for executor <- ZIO.service[DynamoDBExecutor]
      yield Impl(executor)
    }
