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
import search.queue.GameDownloadingProducer
import chessfinder.pubsub.DownloadGameCommand

trait ArchiveDownloader:

  def cache(user: User): φ[TaskId]

object ArchiveDownloader:

  def cache(user: User): ψ[ArchiveDownloader, TaskId] =
    ZIO.serviceWithZIO[ArchiveDownloader](_.cache(user))

  class Impl(
      client: ChessDotComClient,
      userRepo: UserRepo,
      taskRepo: TaskRepo,
      gameDownloadingCommandProducer: GameDownloadingProducer,
      random: Random
  ) extends ArchiveDownloader:

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
        commands = archives.archives.map(archive => DownloadGameCommand(userIdentified, archive, taskId))
        _ <- gameDownloadingCommandProducer.publish(userIdentified, archives, taskId)
      yield taskId

  @Deprecated
  object Impl:
    val layer = ZLayer {
      for
        client                          <- ZIO.service[ChessDotComClient]
        userRepo                        <- ZIO.service[UserRepo]
        taskRepo                        <- ZIO.service[TaskRepo]
        gameDownloadingCommandPublisher <- ZIO.service[GameDownloadingProducer]
        random                          <- ZIO.service[Random]
      yield Impl(client, userRepo, taskRepo, gameDownloadingCommandPublisher, random)
    }
