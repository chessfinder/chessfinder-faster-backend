package chessfinder
package search

import search.entity.*
import zio.ZLayer
import zio.{ UIO, ZIO }
import chessfinder.client.chess_com.ChessDotComClient
import chessfinder.client.ClientError
import search.BrokenLogic
import sttp.model.Uri
import chessfinder.client.chess_com.dto.Games
import chessfinder.client.chess_com.dto.Archives
import annotation.tailrec
import chess.format.pgn.PgnStr
import chessfinder.persistence.GameRecord
import chessfinder.persistence.UserRecord
import chessfinder.persistence.PlatformType
import chessfinder.client.ClientError.ProfileNotFound
import zio.dynamodb.*
import search.repo.*
import api.ApiVersion
import izumi.reflect.Tag
import chessfinder.api.TaskResponse
import chessfinder.search.BrokenLogic.ServiceOverloaded
import zio.Random
import chessfinder.api.TaskStatusResponse
import chessfinder.search.BrokenLogic.NoGameAvaliable
import aspect.Span

trait GameDownloader:

  def cache(user: User): φ[TaskId]

  def download(user: UserIdentified, archives: Archives, taskId: TaskId): φ[Unit]

object GameDownloader:

  def cache(user: User): ψ[GameDownloader, TaskId] =
    ZIO.serviceWithZIO[GameDownloader](_.cache(user))

  def download(user: UserIdentified, archives: Archives, taskId: TaskId): ψ[GameDownloader, Unit] =
    ZIO.serviceWithZIO[GameDownloader](_.download(user, archives, taskId))

  class Impl(
      client: ChessDotComClient,
      userRepo: UserRepo,
      taskRepo: TaskRepo,
      gameRepo: GameRepo,
      random: Random
  ) extends GameDownloader:

    def download(user: UserIdentified, archives: Archives, taskId: TaskId): φ[Unit] =
      val eff = ZIO.foldLeft(archives.archives)(DownloadingResult.empty) { case (previousResult, resource) =>
        val downloadingAndSavingGames =
          for
            _     <- ZIO.logInfo(s"Donwloading the archive ${resource.toString} ...")
            games <- client.games(resource).mapError(_ => ServiceOverloaded)
            _ <- ZIO.logInfo(
              s"Donwloaded ${games.games.length} games from the archive ${resource.toString}!"
            )
            gameRecords = games.games.map(game => HistoricalGame(game.url, PgnStr(game.pgn)))
            _ <- gameRepo.save(user.userId, gameRecords)
            _ <- ZIO.logInfo(s"Games for the archive ${resource.toString} are saved!")
          yield ()

        def processFailure(err: BrokenLogic) =
          for
            _ <- ZIO.logError(s"Failure is registering for ${err}...")
            _ <- taskRepo.failureIncrement(taskId)
            _ <- ZIO.logInfo("Failure is registered ...")
          yield DownloadingResult(resource +: previousResult.failedArchives)

        val processSuccess =
          for
            _ <- ZIO.logInfo("Success is registering ...")
            _ <- taskRepo.successIncrement(taskId)
            _ <- ZIO.logInfo("Success is registered ...")
          yield previousResult

        downloadingAndSavingGames
          .foldZIO(err => processFailure(err), _ => processSuccess)

      }

      eff.unit

    def cache(user: User): φ[TaskId] =
      val gettingProfile = client
        .profile(user.userName)
        .mapError {
          case ClientError.ProfileNotFound(userName) => BrokenLogic.ProfileNotFound(user)
          case _                                     => BrokenLogic.ServiceOverloaded
        }
        .map(profile => user.identified(UserId(profile.`@id`.toString)))

      val gettingArchives = client
        .archives(user.userName)
        .mapError {
          case ClientError.ProfileNotFound(userName) => BrokenLogic.ProfileNotFound(user)
          case _                                     => BrokenLogic.ServiceOverloaded
        }
        .filterOrFail(_.archives.nonEmpty)(NoGameAvaliable(user))

      for
        userIdentified <- gettingProfile
        _              <- userRepo.save(userIdentified)
        archives       <- gettingArchives
        taskId         <- random.nextUUID.map(uuid => TaskId(uuid))
        _              <- taskRepo.initiate(taskId, archives.archives.length)
        _              <- download(userIdentified, archives, taskId).forkDaemon
      yield taskId

  @Deprecated
  object Impl:
    val layer = ZLayer {
      for
        client   <- ZIO.service[ChessDotComClient]
        userRepo <- ZIO.service[UserRepo]
        taskRepo <- ZIO.service[TaskRepo]
        gameRepo <- ZIO.service[GameRepo]
        random   <- ZIO.service[Random]
      yield Impl(client, userRepo, taskRepo, gameRepo, random)
    }
