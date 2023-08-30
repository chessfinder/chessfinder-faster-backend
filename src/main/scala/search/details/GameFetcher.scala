package chessfinder
package search.details

import persistence.GameRecord

import zio.dynamodb.DynamoDBExecutor
import zio.{ ZIO, ZLayer }

trait GameFetcher:
  def list(userId: UserId): Computation[Seq[HistoricalGame]]

object GameFetcher:

  class Impl(executor: DynamoDBExecutor) extends GameFetcher:
    private val layer = ZLayer.succeed(executor)

    override def list(userId: UserId): Computation[Seq[HistoricalGame]] =
      GameRecord.Table
        .list[GameRecord](userId)
        .provideLayer(layer)
        .catchNonFatalOrDie(e =>
          ZIO.logError(e.getMessage()) *> ZIO.fail(BrokenComputation.ServiceOverloaded)
        )
        .map(_.map(_.toGame))

  object Impl:
    val layer = ZLayer {
      for executor <- ZIO.service[DynamoDBExecutor]
      yield Impl(executor)
    }
