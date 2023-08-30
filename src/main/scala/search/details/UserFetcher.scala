package chessfinder
package search.details

import aspect.Span
import persistence.{ PlatformType, UserRecord }

import zio.dynamodb.{ DynamoDBError, DynamoDBExecutor }
import zio.{ Cause, ZIO, ZLayer }

trait UserFetcher:
  def get(user: User): Computation[UserIdentified]

object UserFetcher:

  class Impl(executor: DynamoDBExecutor) extends UserFetcher:
    private val layer = ZLayer.succeed(executor)

    override def get(user: User): Computation[UserIdentified] =
      val eff = UserRecord.Table
        .get[UserRecord](user.userName, PlatformType.fromPlatform(user.platform))
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .flatMap(ZIO.fromEither)
        .tapSomeError { case e: DynamoDBError.DecodingError =>
          ZIO.logErrorCause(e.getMessage(), Cause.fail(e))
        }
        .catchNonFatalOrDie {
          case e: DynamoDBError.ValueNotFound => ZIO.fail(BrokenComputation.ProfileIsNotCached(user))
          case _                              => ZIO.fail(BrokenComputation.ServiceOverloaded)
        }
        .map(_.toUser)
      eff @@ Span.log

  object Impl:
    val layer = ZLayer {
      for executor <- ZIO.service[DynamoDBExecutor]
      yield Impl(executor)
    }
