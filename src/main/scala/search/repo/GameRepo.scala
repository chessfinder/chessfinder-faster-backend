package chessfinder
package search.repo

import chessfinder.search.entity.User
import chessfinder.persistence.UserRecord
import search.entity.*
import zio.{ ZIO, ZLayer }
import zio.dynamodb.DynamoDBExecutor
import persistence.PlatformType
import search.*
import chessfinder.persistence.GameRecord

trait GameRepo:
  def list(user: UserIdentified): φ[Seq[HistoricalGame]]
  def save(userId: UserId, games: Seq[HistoricalGame]): φ[Unit]

object GameRepo:
  def list(user: UserIdentified): ψ[GameRepo, Seq[HistoricalGame]] =
    ψ.serviceWithZIO[GameRepo](_.list(user))

  def save(userId: UserId, games: Seq[HistoricalGame]): ψ[GameRepo, Unit] =
    ψ.serviceWithZIO[GameRepo](_.save(userId, games))

  class Impl(executor: DynamoDBExecutor) extends GameRepo:
    private val layer = ZLayer.succeed(executor)

    override def list(user: UserIdentified): φ[Seq[HistoricalGame]] =
      val fetchedGames =
        GameRecord.Table
          .list[GameRecord](user.userId)
          .provideLayer(layer)
          .catchNonFatalOrDie(e => ZIO.logError(e.getMessage()) *> ZIO.fail(BrokenLogic.ServiceOverloaded))

      for
        maybeGames <- fetchedGames
        games <-
          if (maybeGames.nonEmpty) ZIO.succeed(maybeGames)
          else ZIO.fail(BrokenLogic.NoGameAvaliable(user.user))
        historicalGames = games.map(_.toGame)
      yield historicalGames

    override def save(userId: UserId, games: Seq[HistoricalGame]): φ[Unit] =
      val records = games.map(game => GameRecord.fromGame(userId, game))
      GameRecord.Table
        .putMany(records*)
        .provideLayer(layer)
        .catchNonFatalOrDie(e => ZIO.logError(e.getMessage()) *> ZIO.fail(BrokenLogic.ServiceOverloaded))

  object Impl:
    val layer = ZLayer {
      for executor <- ZIO.service[DynamoDBExecutor]
      yield Impl(executor)
    }
