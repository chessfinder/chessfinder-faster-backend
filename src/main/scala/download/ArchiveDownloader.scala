package chessfinder
package download

import client.ClientError
import client.ClientError.ProfileNotFound
import client.chess_com.ChessDotComClient
import BrokenComputation.{ NoGameAvailable, ServiceOverloaded }
import search.*

import chessfinder.download.details.{
  ArchiveRepo,
  DownloadGameCommandPublisher,
  DownloadStatusResponse,
  TaskRepo,
  UserRegister
}
import zio.{ Random, ZIO, ZLayer }

trait ArchiveDownloader:

  def cache(user: User): Computation[TaskId]

object ArchiveDownloader:

  class Impl(
      client: ChessDotComClient,
      userRepo: UserRegister,
      archiveRepo: ArchiveRepo,
      taskRepo: TaskRepo,
      gameDownloadingCommandProducer: DownloadGameCommandPublisher,
      random: Random
  ) extends ArchiveDownloader:

    def cache(user: User): Computation[TaskId] =
      val gettingProfile = client
        .profile(user.userName)
        .tapError(err => ZIO.log(err.toString()))
        .mapError {
          case ClientError.ProfileNotFound(_) => BrokenComputation.ProfileNotFound(user)
          case _                              => BrokenComputation.ServiceOverloaded
        }
        .map(profile => user.identified(UserId(profile.`@id`.toString)))

      val gettingArchives = client
        .archives(user.userName)
        .mapError {
          case ClientError.ProfileNotFound(_) => BrokenComputation.ProfileNotFound(user)
          case _                              => BrokenComputation.ServiceOverloaded
        }
        .filterOrFail(_.archives.nonEmpty)(NoGameAvailable(user))

      for
        userIdentified <- gettingProfile
        _              <- ZIO.log("Trying to save the user")
        _              <- userRepo.save(userIdentified)
        allArchives    <- gettingArchives

        _                    <- ZIO.log("Trying to get archives")
        cachedArchiveResults <- archiveRepo.getAll(userIdentified.userId)
        cachedArchives = cachedArchiveResults.map(_.resource).toSet
        fullyDownloadedArchives = cachedArchiveResults
          .filter(_.status == ArchiveStatus.FullyDownloaded)
          .map(_.resource)
          .toSet
        shouldBeDownloadedArchives = allArchives.archives.filterNot(resource =>
          fullyDownloadedArchives.contains(resource)
        )
        missingArchives = allArchives.archives.filterNot(resource => cachedArchives.contains(resource))
        _      <- ZIO.log("Trying to write archives")
        _      <- archiveRepo.initiate(userIdentified.userId, missingArchives)
        taskId <- random.nextUUID.map(uuid => TaskId(uuid))
        _      <- ZIO.log("Trying to create task")
        _      <- taskRepo.initiate(taskId, shouldBeDownloadedArchives.length)
        shouldBeDownloadedArchivesIds = shouldBeDownloadedArchives.map(resource =>
          ArchiveId(resource.toString)
        )
        _ <- ZIO.log("Trying to send event")
        _ <- gameDownloadingCommandProducer.publish(userIdentified, shouldBeDownloadedArchivesIds, taskId)
      yield taskId

  object Impl:
    val layer = ZLayer {
      for
        client                          <- ZIO.service[ChessDotComClient]
        userRepo                        <- ZIO.service[UserRegister]
        taskRepo                        <- ZIO.service[TaskRepo]
        archiveRepo                     <- ZIO.service[ArchiveRepo]
        gameDownloadingCommandPublisher <- ZIO.service[DownloadGameCommandPublisher]
        random                          <- ZIO.service[Random]
      yield Impl(client, userRepo, archiveRepo, taskRepo, gameDownloadingCommandPublisher, random)
    }
