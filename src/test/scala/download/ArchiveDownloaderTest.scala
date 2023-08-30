package chessfinder
package download

import BrokenComputation.*
import client.ClientError
import client.chess_com.*
import core.{ ProbabilisticBoard, SearchFen }
import download.{ ArchiveDownloader, GameDownloader }
import search.*
import util.UriParser

import chess.format.pgn.PgnStr
import chessfinder.client.chess_com.{ Archives, Profile }
import chessfinder.download.details.{ DownloadResponse, DownloadStatusResponse }
import sttp.model.Uri
import sttp.model.Uri.UriContext
import zio.ZIO
import zio.mock.{ Expectation, MockRandom, MockReporter }
import zio.test.*

import java.util.UUID

object ArchiveDownloaderTest extends ZIOSpecDefault with Mocks:

  val service = ZIO.service[ArchiveDownloader]

  override def spec = suite("ArchiveDownloader")(
    suite("cache")(
      test(
        "when a valid user is prvided should get the profie of the user, then archives and send DownloadGameCommand to queue"
      ) {
        val platform = ChessPlatform.ChessDotCom
        val userName = UserName("tigran-c-137")
        val user     = chessfinder.User(platform, userName)
        val expectedProfile =
          Profile(`@id` = uri"https://api.chess.com/pub/player/tigran-c-137")

        val `getting profile from chess.com` =
          ChessDotComClientMock.ProfileMethod(
            assertion = Assertion.equalTo(userName),
            result = Expectation.value(expectedProfile)
          )

        val userId         = UserId(expectedProfile.`@id`.toString)
        val userIdentified = user.identified(userId)

        val `saving user into database` =
          UserRegisterMock.SaveUser(
            assertion = Assertion.equalTo(userIdentified),
            result = Expectation.value(())
          )

        val `requesting user's archives from chess.com` =
          val archives = Archives(
            Seq(
              uri"https://example.com/archive/2022/1",
              uri"https://example.com/archive/2022/2",
              uri"https://example.com/archive/2022/3",
              uri"https://example.com/archive/2022/4",
              uri"https://example.com/archive/2022/5"
            )
          )
          ChessDotComClientMock.ArchivesMethod(
            assertion = Assertion.equalTo(userName),
            result = Expectation.value(archives)
          )

        val `getting users archives from database` =
          val archiveFullyDownloaded = ArchiveResult(
            userId: UserId,
            uri"https://example.com/archive/2022/1"
          ).toOption.get.copy(status = ArchiveStatus.FullyDownloaded)

          val archivePartiallyDownloaded = ArchiveResult(
            userId: UserId,
            uri"https://example.com/archive/2022/2"
          ).toOption.get.copy(status = ArchiveStatus.PartiallyDownloaded)

          val archiveUntouched = ArchiveResult(
            userId: UserId,
            uri"https://example.com/archive/2022/3"
          ).toOption.get.copy(status = ArchiveStatus.NotDownloaded)

          val archives = Seq(archiveFullyDownloaded, archivePartiallyDownloaded, archiveUntouched)
          ArchiveRepoMock.GetAllMethod(
            assertion = Assertion.equalTo(userId),
            result = Expectation.value(archives)
          )

        val `saving missing archives into database` =
          val missingArchives = Seq(
            uri"https://example.com/archive/2022/4",
            uri"https://example.com/archive/2022/5"
          )
          ArchiveRepoMock.InitiateMethod(
            assertion = Assertion.equalTo((userId, missingArchives)),
            result = Expectation.unit
          )

        val taskId = TaskId(UUID.randomUUID())

        val `generating taskId` = MockRandom.NextUUID(
          Expectation.value(taskId.value)
        )

        val `initiating task` =
          val numberOfDownloadableArchives = 4
          val task = DownloadStatusResponse(
            taskId = taskId.value,
            succeed = 0,
            failed = 0,
            done = 0,
            pending = numberOfDownloadableArchives,
            total = numberOfDownloadableArchives
          )
          TaskRepoMock.InitiateTask(
            assertion = Assertion.equalTo((taskId, numberOfDownloadableArchives)),
            result = Expectation.value(task)
          )

        val `sending DownloadGameCommand to queue` =
          val downloadableArchive = Seq(
            ArchiveId("https://example.com/archive/2022/2"),
            ArchiveId("https://example.com/archive/2022/3"),
            ArchiveId("https://example.com/archive/2022/4"),
            ArchiveId("https://example.com/archive/2022/5")
          )
          GameDownloadingProducerMock.PublishMethod(
            assertion = Assertion.equalTo((userIdentified, downloadableArchive, taskId)),
            result = Expectation.unit
          )

        val mock =
          (`getting profile from chess.com` ++
            `saving user into database` ++
            `requesting user's archives from chess.com` ++
            `getting users archives from database` ++
            `saving missing archives into database` ++
            `generating taskId` ++
            `initiating task` ++
            `sending DownloadGameCommand to queue`).toLayer

        val testResult =
          for
            archiveDownloader <- service
            actualResult      <- archiveDownloader.cache(user)
            check = assertTrue(actualResult == taskId)
          yield check
        testResult.provide(mock, ArchiveDownloader.Impl.layer)
      },
      test(
        "when user profile is not found should return ProfileNotFound"
      ) {
        val platform = ChessPlatform.ChessDotCom
        val userName = UserName("tigran-c-137")
        val user     = chessfinder.User(platform, userName)
        val expectedProfile =
          Profile(`@id` = uri"https://api.chess.com/pub/player/tigran-c-137")

        val `getting profile from chess.com` =
          ChessDotComClientMock.ProfileMethod(
            assertion = Assertion.equalTo(userName),
            result = Expectation.failure(ClientError.ProfileNotFound(userName))
          )

        val mock =
          `getting profile from chess.com`.toLayer

        val testResult =
          for
            archiveDownloader <- service
            actualResult      <- archiveDownloader.cache(user).either
            check = assertTrue(actualResult == Left(ProfileNotFound(user)))
          yield check
        testResult.provide(
          mock,
          UserRegisterMock.empty,
          TaskRepoMock.empty,
          GameDownloadingProducerMock.empty,
          MockRandom.empty,
          ArchiveRepoMock.empty,
          ArchiveDownloader.Impl.layer
        )
      },
      test(
        "when user profile is found but archives are not available should return ProfileNotFound"
      ) {
        val platform = ChessPlatform.ChessDotCom
        val userName = UserName("tigran-c-137")
        val user     = chessfinder.User(platform, userName)
        val expectedProfile =
          Profile(`@id` = uri"https://api.chess.com/pub/player/tigran-c-137")

        val `getting profile from chess.com` =
          ChessDotComClientMock.ProfileMethod(
            assertion = Assertion.equalTo(userName),
            result = Expectation.value(expectedProfile)
          )

        val userId         = UserId(expectedProfile.`@id`.toString)
        val userIdentified = user.identified(userId)

        val `saving user into database` =
          UserRegisterMock.SaveUser(
            assertion = Assertion.equalTo(userIdentified),
            result = Expectation.value(())
          )

        val `requesting user's archives from chess.com` =
          ChessDotComClientMock.ArchivesMethod(
            assertion = Assertion.equalTo(userName),
            result = Expectation.failure(ClientError.ProfileNotFound(userName))
          )

        val mock =
          (`getting profile from chess.com` ++
            `saving user into database` ++
            `requesting user's archives from chess.com`).toLayer

        val testResult =
          for
            archiveDownloader <- service
            actualResult      <- archiveDownloader.cache(user).either
            check = assertTrue(actualResult == Left(ProfileNotFound(user)))
          yield check

        testResult.provide(
          mock,
          TaskRepoMock.empty,
          GameDownloadingProducerMock.empty,
          MockRandom.empty,
          ArchiveRepoMock.empty,
          ArchiveDownloader.Impl.layer
        )
      },
      test(
        "when a valid user is prvided archives are empty should return NoGameAvaliable"
      ) {
        val platform = ChessPlatform.ChessDotCom
        val userName = UserName("tigran-c-137")
        val user     = chessfinder.User(platform, userName)
        val expectedProfile =
          Profile(`@id` = uri"https://api.chess.com/pub/player/tigran-c-137")

        val `getting profile from chess.com` =
          ChessDotComClientMock.ProfileMethod(
            assertion = Assertion.equalTo(userName),
            result = Expectation.value(expectedProfile)
          )

        val userId         = UserId(expectedProfile.`@id`.toString)
        val userIdentified = user.identified(userId)

        val `saving user into database` =
          UserRegisterMock.SaveUser(
            assertion = Assertion.equalTo(userIdentified),
            result = Expectation.value(())
          )

        val `requesting user's archives from chess.com` =
          val archives = Archives(
            Seq.empty[Uri]
          )
          ChessDotComClientMock.ArchivesMethod(
            assertion = Assertion.equalTo(userName),
            result = Expectation.value(archives)
          )

        val mock =
          (`getting profile from chess.com` ++
            `saving user into database` ++
            `requesting user's archives from chess.com`).toLayer

        val testResult =
          for
            archiveDownloader <- service
            actualResult      <- archiveDownloader.cache(user).either
            check = assertTrue(actualResult == Left(NoGameAvailable(user)))
          yield check
        testResult.provide(
          mock,
          GameDownloadingProducerMock.empty,
          TaskRepoMock.empty,
          ArchiveRepoMock.empty,
          ArchiveDownloader.Impl.layer,
          MockRandom.empty
        )
      }
    )
  ) @@ TestAspect.sequential @@ MockReporter()
