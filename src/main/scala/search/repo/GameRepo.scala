package chessfinder
package search.repo

import aspect.Span
import persistence.{ GameRecord, PlatformType, UserRecord }
import search.*
import search.entity.*

import zio.dynamodb.DynamoDBExecutor
import zio.{ Cause, ZIO, ZLayer }

trait GameRepo:
  def list(userId: UserId): φ[Seq[HistoricalGame]]
  def save(userId: UserId, games: Seq[HistoricalGame]): φ[Unit]

object GameRepo:

  class Impl(executor: DynamoDBExecutor) extends GameRepo:
    private val layer = ZLayer.succeed(executor)

    override def list(userId: UserId): φ[Seq[HistoricalGame]] =
      GameRecord.Table
        .list[GameRecord](userId)
        .provideLayer(layer)
        .catchNonFatalOrDie(e => ZIO.logError(e.getMessage()) *> ZIO.fail(BrokenLogic.ServiceOverloaded))
        .map(_.map(_.toGame))

    override def save(userId: UserId, games: Seq[HistoricalGame]): φ[Unit] =
      val eff =
        val records = games.map(game => GameRecord.fromGame(userId, game))
        GameRecord.Table
          .putMany(records*)
          .provideLayer(layer)
          .catchNonFatalOrDie(e => ZIO.logError(e.getMessage()) *> ZIO.fail(BrokenLogic.ServiceOverloaded))

      val effLogged = (ZIO.logInfo(s"Saving ${games.length} into table ...") *> eff).tapBoth(
        e => ZIO.logErrorCause(s"Failed to save ${games.length}!", Cause.fail(e)),
        _ => ZIO.logInfo(s"Successfully saved ${games.length} into table!")
      )
      effLogged @@ Span.log

  object Impl:
    val layer = ZLayer {
      for executor <- ZIO.service[DynamoDBExecutor]
      yield Impl(executor)
    }
