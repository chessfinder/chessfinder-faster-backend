package chessfinder
package download.details

import aspect.Span
import persistence.GameRecord

import zio.dynamodb.DynamoDBExecutor
import zio.{ Cause, ZIO, ZLayer }

trait GameSaver:
  def save(userId: UserId, games: Seq[HistoricalGame]): Computation[Unit]

object GameSaver:

  class Impl(executor: DynamoDBExecutor) extends GameSaver:
    private val layer = ZLayer.succeed(executor)

    override def save(userId: UserId, games: Seq[HistoricalGame]): Computation[Unit] =
      val eff =
        val records = games.map(game => GameRecord.fromGame(userId, game))
        GameRecord.Table
          .putMany(records*)
          .provideLayer(layer)
          .catchNonFatalOrDie(e =>
            ZIO.logError(e.getMessage()) *> ZIO.fail(BrokenComputation.ServiceOverloaded)
          )

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
