package chessfinder
package search

import zio.test.*
import chessfinder.core.SearchFen
import chessfinder.core.ProbabilisticBoard
import search.BrokenLogic.*
import search.GameDownloader
import search.entity.*
import sttp.model.Uri.UriContext
import client.chess_com.dto.*
import chess.format.pgn.PgnStr
import zio.mock.Expectation
import api.ApiVersion
import core.SearchFen
import chessfinder.api.TaskResponse
import chessfinder.util.UriParser
import java.util.UUID
import zio.mock.MockRandom
import client.ClientError
import sttp.model.Uri
import chess.format.pgn.PgnStr
import zio.mock.MockReporter
import chessfinder.search.queue.GameDownloadingProducer

object ArchiveDownloaderTest extends ZIOSpecDefault with Mocks:

  override def spec = suite("ArchiveDownloader")(
    suite("cache")(
      test(
        "when a valid user is prvided should get the profie of the user, then archives and launch a process of downloading games (this can't be check since it is launched as a deamon fiber)"
      ) {
        val platform = ChessPlatform.ChessDotCom
        val userName = UserName("user")
        val user     = User(platform, userName)
        val expectedProfile =
          Profile(`@id` = uri"https://api.chess.com/pub/player/tigran-c-137")

        val getProfile = ChessDotComClientMock.ProfileMethod(
          assertion = Assertion.equalTo(userName),
          result = Expectation.value(expectedProfile)
        )

        val userIdentified = user.identified(UserId(expectedProfile.`@id`.toString))

        val saveUser = UserRepoMock.SaveUser(
          assertion = Assertion.equalTo(userIdentified),
          result = Expectation.value(())
        )

        val expectedArchive =
          Archives(Seq(uri"https://example.com/archive/1", uri"https://example.com/archive/2"))

        val getArchiveCall = ChessDotComClientMock.ArchivesMethod(
          assertion = Assertion.equalTo(userName),
          result = Expectation.value(expectedArchive)
        )

        val expectedUuid = UUID.randomUUID()

        val generateTaskId = MockRandom.NextUUID(
          Expectation.value(expectedUuid)
        )

        val expectedTaskId = TaskId(expectedUuid)

        val initiatingTask = TaskRepoMock.InitiateTask(
          assertion = Assertion.equalTo((expectedTaskId, expectedArchive.archives.length)),
          result = Expectation.unit
        )

        val firingCommands = GameDownloadingProducerMock.PublishMethod(
          assertion = Assertion.equalTo((userIdentified, expectedArchive, expectedTaskId)),
          result = Expectation.unit
        )

        val mock =
          (getProfile ++ saveUser ++ getArchiveCall ++ generateTaskId ++ initiatingTask ++ firingCommands).toLayer

        val caching = ArchiveDownloader
          .cache(user)

        (for
          actualResult <- caching
          check = assertTrue(actualResult == expectedTaskId)
        yield check).provide(mock, ArchiveDownloader.Impl.layer)
      },
      test("when user profile is not found should return ProfileNotFound") {

        val platform = ChessPlatform.ChessDotCom
        val userName = UserName("user")
        val user     = User(platform, userName)
        val expectedProfile =
          Profile(`@id` = UriParser.apply("https://api.chess.com/pub/player/tigran-c-137").get)

        val getProfile = ChessDotComClientMock.ProfileMethod(
          assertion = Assertion.equalTo(userName),
          result = Expectation.failure(ClientError.ProfileNotFound(userName))
        )

        val mock = getProfile.toLayer

        val caching = ArchiveDownloader
          .cache(user)

        (for
          actualResult <- caching.either
          check = assertTrue(actualResult == Left(ProfileNotFound(user)))
        yield check)
          .provide(
            mock,
            UserRepoMock.empty,
            TaskRepoMock.empty,
            GameDownloadingProducerMock.empty,
            ArchiveDownloader.Impl.layer,
            MockRandom.empty
          )
      },
      test("when user profile is found but archives are not available should return ProfileNotFound") {

        val platform = ChessPlatform.ChessDotCom
        val userName = UserName("user")
        val user     = User(platform, userName)
        val expectedProfile =
          Profile(`@id` = UriParser.apply("https://api.chess.com/pub/player/tigran-c-137").get)

        val getProfile = ChessDotComClientMock.ProfileMethod(
          assertion = Assertion.equalTo(userName),
          result = Expectation.value(expectedProfile)
        )

        val userIdentified = user.identified(UserId(expectedProfile.`@id`.toString))

        val saveUser = UserRepoMock.SaveUser(
          assertion = Assertion.equalTo(userIdentified),
          result = Expectation.value(())
        )

        val getArchiveCall = ChessDotComClientMock.ArchivesMethod(
          assertion = Assertion.equalTo(userName),
          result = Expectation.failure(ClientError.ProfileNotFound(userName))
        )

        val mock = (getProfile ++ saveUser ++ getArchiveCall).toLayer

        val caching = ArchiveDownloader
          .cache(user)

        (for
          actualResult <- caching.either
          check = assertTrue(actualResult == Left(ProfileNotFound(user)))
        yield check)
          .provide(
            mock,
            TaskRepoMock.empty,
            GameDownloadingProducerMock.empty,
            ArchiveDownloader.Impl.layer,
            MockRandom.empty
          )
      },
      test(
        "when a valid user is prvided archives are empty should return NoGameAvaliable"
      ) {
        val platform = ChessPlatform.ChessDotCom
        val userName = UserName("user")
        val user     = User(platform, userName)
        val expectedProfile =
          Profile(`@id` = UriParser.apply("https://api.chess.com/pub/player/tigran-c-137").get)

        val getProfile = ChessDotComClientMock.ProfileMethod(
          assertion = Assertion.equalTo(userName),
          result = Expectation.value(expectedProfile)
        )

        val userIdentified = user.identified(UserId(expectedProfile.`@id`.toString))

        val saveUser = UserRepoMock.SaveUser(
          assertion = Assertion.equalTo(userIdentified),
          result = Expectation.value(())
        )

        val expectedArchive =
          Archives(Seq.empty[Uri])

        val getArchiveCall = ChessDotComClientMock.ArchivesMethod(
          assertion = Assertion.equalTo(userName),
          result = Expectation.value(expectedArchive)
        )

        val mock = (getProfile ++ saveUser ++ getArchiveCall).toLayer

        val caching = ArchiveDownloader
          .cache(user)

        (for
          actualResult <- caching.either
          check = assertTrue(actualResult == Left(NoGameAvaliable(user)))
        yield check)
          .provide(
            mock,
            GameDownloadingProducerMock.empty,
            TaskRepoMock.empty,
            ArchiveDownloader.Impl.layer,
            MockRandom.empty
          )
      }
    )
  ) @@ TestAspect.sequential @@ MockReporter()
