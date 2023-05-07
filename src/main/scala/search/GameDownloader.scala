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

      @tailrec def rec(result: φ[DownloadingResult], archives: List[Uri]): φ[DownloadingResult] =
        archives match
          case resource :: tail =>
            val downloadingAndSavingGames =
              for
                games <- client.games(resource).mapError(_ => ServiceOverloaded)
                gameRecords = games.games.map(game => HistoricalGame(game.url, PgnStr(game.pgn)))
                _ <- gameRepo.save(user.userId, gameRecords)
              yield ()

            val processFailure =
              for
                _   <- taskRepo.failureIncrement(taskId)
                res <- result
              yield DownloadingResult(resource +: res.failedArchives)
            val processSuccess = taskRepo.successIncrement(taskId) *> result
            val downloadingAndSavingGamesRecovered = downloadingAndSavingGames
              .foldZIO(err => processFailure, _ => processSuccess)
            rec(downloadingAndSavingGamesRecovered, tail)
          case _ => result

      rec(φ.succeed(DownloadingResult.empty), archives.archives.toList.reverse).unit

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
