package chessfinder
package search

import api.{ TaskResponse, TaskStatusResponse }
import aspect.Span
import client.ClientError
import client.ClientError.ProfileNotFound
import client.chess_com.ChessDotComClient
import client.chess_com.dto.{ Archives, Games }
import persistence.{ GameRecord, PlatformType, UserRecord }
import search.BrokenLogic
import search.BrokenLogic.{ NoGameAvailable, ServiceOverloaded }
import search.entity.*
import search.repo.*

import chess.format.pgn.PgnStr
import izumi.reflect.Tag
import sttp.model.Uri
import zio.dynamodb.*
import zio.{ Clock, Random, UIO, ZIO, ZLayer }

import scala.annotation.tailrec

trait GameDownloader:

  def download(user: UserIdentified, archive: ArchiveId, taskId: TaskId): φ[Unit]

object GameDownloader:

  class Impl(
      client: ChessDotComClient,
      archiveRepo: ArchiveRepo,
      taskRepo: TaskRepo,
      gameRepo: GameRepo,
      clock: Clock
  ) extends GameDownloader:

    def download(user: UserIdentified, archiveId: ArchiveId, taskId: TaskId): φ[Unit] =
      val downloadingAndSavingGames =
        for
          _       <- ZIO.logInfo(s"Checking archive ${archiveId.value} before downloading...")
          archive <- archiveRepo.get(user.userId, archiveId)
          _ <-
            if archive.status == ArchiveStatus.FullyDownloaded
            then ZIO.logInfo(s"Archive ${archiveId.value} was already download (games ${archive.downloaded})")
            else
              for
                _     <- ZIO.logInfo(s"Archive ${archiveId.value} is downloading...")
                games <- client.games(archive.resource).mapError(_ => ServiceOverloaded)
                _ <- ZIO.logInfo(
                  s"Downloaded ${games.games.length} games from the archive ${archiveId.value}!"
                )
                gameRecords = games.games.map(game =>
                  HistoricalGame.WithTimePlayed(game.url, PgnStr(game.pgn), game.end_time)
                )
                lastGame = archive.lastGamePlayed.flatMap(id => gameRecords.find(_.game.id == id))
                newGames = lastGame
                  .map(game => gameRecords.filter(_.endTimeEpoch > game.endTimeEpoch))
                  .getOrElse(gameRecords)
                _ <- gameRepo.save(user.userId, newGames.map(_.game))
                _ <- ZIO.logInfo(
                  s"Games in total ${newGames.length} for the archive ${archive.archiveId} are saved!"
                )
                now <- clock.instant
                lastPlayed     = gameRecords.maxByOption(_.endTimeEpoch).map(_.game.id)
                updatedArchive = archive.update(lastPlayed, newGames.length, now)
                _ <- archiveRepo.update(updatedArchive)
              yield ()
        yield ()

      def processFailure(err: BrokenLogic) =
        for
          _ <- ZIO.logError(s"Failure is registering for ${err}...")
          _ <- taskRepo.failureIncrement(taskId).ignore
          _ <- ZIO.logInfo("Failure is registered ...")
        yield ()

      val processSuccess =
        for
          _ <- ZIO.logInfo("Success is registering ...")
          _ <- taskRepo.successIncrement(taskId).ignore
          _ <- ZIO.logInfo("Success is registered ...")
        yield ()

      downloadingAndSavingGames
        .foldZIO(err => processFailure(err), _ => processSuccess)

  object Impl:
    val layer = ZLayer {
      for
        client      <- ZIO.service[ChessDotComClient]
        archiveRepo <- ZIO.service[ArchiveRepo]
        taskRepo    <- ZIO.service[TaskRepo]
        gameRepo    <- ZIO.service[GameRepo]
        clock       <- ZIO.service[Clock]
      yield Impl(client, archiveRepo, taskRepo, gameRepo, clock)
    }
