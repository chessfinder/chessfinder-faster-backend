package chessfinder
package search.repo

import chessfinder.search.entity.User
import chessfinder.persistence.UserRecord
import search.entity.*
import zio.{ ZIO, ZLayer }
import zio.dynamodb.DynamoDBExecutor
import persistence.PlatformType
import search.*
import zio.dynamodb.DynamoDBError
import zio.Cause
import aspect.Span

trait UserRepo:
  def get(user: User): φ[UserIdentified]

  def save(user: UserIdentified): φ[Unit]

object UserRepo:
  def get(user: User): ψ[UserRepo, UserIdentified] =
    ψ.serviceWithZIO[UserRepo](_.get(user))

  def save(user: UserIdentified): ψ[UserRepo, Unit] =
    ψ.serviceWithZIO[UserRepo](_.save(user))

  class Impl(executor: DynamoDBExecutor) extends UserRepo:
    private val layer = ZLayer.succeed(executor)

    override def get(user: User): φ[UserIdentified] =
      val eff = UserRecord.Table
        .get[UserRecord](user.userName, PlatformType.fromPlatform(user.platform))
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .flatMap(ZIO.fromEither)
        .tapSomeError { case e: DynamoDBError.DecodingError =>
          ZIO.logErrorCause(e.getMessage(), Cause.fail(e))
        }
        .catchNonFatalOrDie {
          case e: DynamoDBError.ValueNotFound => ZIO.fail(BrokenLogic.ProfileIsNotCached(user))
          case _                              => ZIO.fail(BrokenLogic.ServiceOverloaded)
        }
        .map(_.toUser)
      eff @@ Span.log

    override def save(user: UserIdentified): φ[Unit] =
      val eff = UserRecord.Table
        .put(UserRecord.fromUserIdentified(user))
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .mapError(_ => BrokenLogic.ServiceOverloaded)
      eff @@ Span.log

  object Impl:
    val layer = ZLayer {
      for executor <- ZIO.service[DynamoDBExecutor]
      yield Impl(executor)
    }
