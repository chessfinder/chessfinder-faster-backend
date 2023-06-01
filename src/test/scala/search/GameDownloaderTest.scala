package chessfinder
package search

import api.TaskResponse
import client.ClientError
import client.chess_com.dto.*
import core.{ ProbabilisticBoard, SearchFen }
import search.BrokenLogic.*
import search.GameDownloader
import search.entity.*
import sttp.model.Uri
import sttp.model.Uri.UriContext
import util.UriParser

import chess.format.pgn.PgnStr
import zio.ZIO
import zio.mock.{ Expectation, MockClock, MockRandom, MockReporter }
import zio.test.*

import java.time.*
import java.util.UUID
import sttp.model.Uri.UriContext

object GameDownloaderTest extends ZIOSpecDefault with Mocks:

  val service = ZIO.service[GameDownloader]

  override def spec = suite("GameDownloader")(
    suite("download")(
      test(
        "when a valid user is prvided with the archive and archive is in status NotDownloaded should download games and save in database"
      ) {
        val platform       = ChessPlatform.ChessDotCom
        val userName       = UserName("user")
        val user           = User(platform, userName)
        val userId         = UserId("https://api.chess.com/pub/player/tigran-c-137")
        val userIdentified = user.identified(userId)
        val taskId         = TaskId(UUID.randomUUID())
        val archiveId      = ArchiveId("https://example.com/archive/2022/1")
        val archiveContainsGamesTill =
          LocalDate.of(2022, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val now = LocalDate.of(2022, 2, 2).atStartOfDay(ZoneOffset.UTC).toInstant()

        val `get the archive` =
          val existingArchive = ArchiveResult(
            userId,
            archiveId,
            uri"https://example.com/archive/2022/1",
            archiveContainsGamesTill,
            None,
            0,
            ArchiveStatus.NotDownloaded
          )
          ArchiveRepoMock.GetMethod(
            assertion = Assertion.equalTo((userId, archiveId)),
            result = Expectation.value(existingArchive)
          )

        val `download the archive` =
          val downloadedGame1   = Game(uri"https://example.com/1", "Game1", 111L)
          val downloadedGame2   = Game(uri"https://example.com/2", "Game2", 222L)
          val downloadedArchive = Games(Seq(downloadedGame1, downloadedGame2))
          ChessDotComClientMock.GamesMethod(
            assertion = Assertion.equalTo(uri"https://example.com/archive/2022/1"),
            result = Expectation.value(downloadedArchive)
          )

        val `save downloaded games` =
          val historicalGame1 = HistoricalGame(uri"https://example.com/1", PgnStr("Game1"))
          val historicalGame2 = HistoricalGame(uri"https://example.com/2", PgnStr("Game2"))
          GameRepoMock.SaveGames(
            assertion = Assertion.equalTo((userId, Seq(historicalGame1, historicalGame2))),
            result = Expectation.unit
          )

        val `check the time in order to decide if the archive is fully downloaded or not` =
          MockClock.Instant(Expectation.value(now))

        val `update archive status` =
          val updatedArchive = ArchiveResult(
            userId,
            archiveId,
            uri"https://example.com/archive/2022/1",
            archiveContainsGamesTill,
            Some(GameId("https://example.com/2")),
            2,
            ArchiveStatus.FullyDownloaded
          )
          ArchiveRepoMock.UpdateMethod(
            assertion = Assertion.equalTo(updatedArchive),
            result = Expectation.unit
          )

        val `update task status` = TaskRepoMock.SuccessIncrement(
          assertion = Assertion.equalTo(taskId),
          result = Expectation.unit
        )

        val mock =
          (`get the archive` ++
            `download the archive` ++
            `save downloaded games` ++
            `check the time in order to decide if the archive is fully downloaded or not` ++
            `update archive status` ++
            `update task status`).toLayer

        val testResult =
          for
            gameDownloader <- service
            actualResult   <- gameDownloader.download(userIdentified, archiveId, taskId)
            check = assertTrue(actualResult == ())
          yield check
        testResult.provide(mock, GameDownloader.Impl.layer)
      },
      test(
        "when a valid user is prvided with the archive and archive is in status PartiallyDownloaded should download the remaing games and save in database"
      ) {
        val platform       = ChessPlatform.ChessDotCom
        val userName       = UserName("user")
        val user           = User(platform, userName)
        val userId         = UserId("https://api.chess.com/pub/player/tigran-c-137")
        val userIdentified = user.identified(userId)
        val taskId         = TaskId(UUID.randomUUID())
        val archiveId      = ArchiveId("https://example.com/archive/2022/1")
        val archiveContainsGamesTill =
          LocalDate.of(2022, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val now = LocalDate.of(2022, 2, 2).atStartOfDay(ZoneOffset.UTC).toInstant()

        val `get the archive` =
          val existingArchive = ArchiveResult(
            userId,
            archiveId,
            uri"https://example.com/archive/2022/1",
            archiveContainsGamesTill,
            Some(GameId("https://example.com/1")),
            2,
            ArchiveStatus.PartiallyDownloaded
          )
          ArchiveRepoMock.GetMethod(
            assertion = Assertion.equalTo((userId, archiveId)),
            result = Expectation.value(existingArchive)
          )

        val `download the archive` =
          val downloadedGame0   = Game(uri"https://example.com/0", "Game1", 11L)
          val downloadedGame1   = Game(uri"https://example.com/1", "Game1", 111L)
          val downloadedGame2   = Game(uri"https://example.com/2", "Game2", 222L)
          val downloadedArchive = Games(Seq(downloadedGame0, downloadedGame1, downloadedGame2))
          ChessDotComClientMock.GamesMethod(
            assertion = Assertion.equalTo(uri"https://example.com/archive/2022/1"),
            result = Expectation.value(downloadedArchive)
          )

        val `save downloaded games` =
          val historicalGame2 = HistoricalGame(uri"https://example.com/2", PgnStr("Game2"))
          GameRepoMock.SaveGames(
            assertion = Assertion.equalTo((userId, Seq(historicalGame2))),
            result = Expectation.unit
          )

        val `check the time in order to decide if the archive is fully downloaded or not` =
          MockClock.Instant(Expectation.value(now))

        val `update archive status` =
          val updatedArchive = ArchiveResult(
            userId,
            archiveId,
            uri"https://example.com/archive/2022/1",
            archiveContainsGamesTill,
            Some(GameId("https://example.com/2")),
            3,
            ArchiveStatus.FullyDownloaded
          )
          ArchiveRepoMock.UpdateMethod(
            assertion = Assertion.equalTo(updatedArchive),
            result = Expectation.unit
          )

        val `update task status` = TaskRepoMock.SuccessIncrement(
          assertion = Assertion.equalTo(taskId),
          result = Expectation.unit
        )

        val mock =
          (`get the archive` ++
            `download the archive` ++
            `save downloaded games` ++
            `check the time in order to decide if the archive is fully downloaded or not` ++
            `update archive status` ++
            `update task status`).toLayer

        val testResult =
          for
            gameDownloader <- service
            actualResult   <- gameDownloader.download(userIdentified, archiveId, taskId)
            check = assertTrue(actualResult == ())
          yield check
        testResult.provide(mock, GameDownloader.Impl.layer)
      },
      test(
        "when a valid user is prvided with the archive and archive is in status FullyDownloaded should skip the process and mark it as successfully done"
      ) {
        val platform       = ChessPlatform.ChessDotCom
        val userName       = UserName("user")
        val user           = User(platform, userName)
        val userId         = UserId("https://api.chess.com/pub/player/tigran-c-137")
        val userIdentified = user.identified(userId)
        val taskId         = TaskId(UUID.randomUUID())
        val archiveId      = ArchiveId("https://example.com/archive/2022/1")
        val archiveContainsGamesTill =
          LocalDate.of(2022, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val now = LocalDate.of(2022, 2, 2).atStartOfDay(ZoneOffset.UTC).toInstant()

        val `get the archive` =
          val existingArchive = ArchiveResult(
            userId,
            archiveId,
            uri"https://example.com/archive/2022/1",
            archiveContainsGamesTill,
            Some(GameId("https://example.com/1")),
            2,
            ArchiveStatus.FullyDownloaded
          )
          ArchiveRepoMock.GetMethod(
            assertion = Assertion.equalTo((userId, archiveId)),
            result = Expectation.value(existingArchive)
          )

        val `update task status` = TaskRepoMock.SuccessIncrement(
          assertion = Assertion.equalTo(taskId),
          result = Expectation.unit
        )

        val mock =
          (`get the archive` ++
            `update task status`).toLayer

        val testResult =
          for
            gameDownloader <- service
            actualResult   <- gameDownloader.download(userIdentified, archiveId, taskId)
            check = assertTrue(actualResult == ())
          yield check
        testResult.provide(
          mock,
          ChessDotComClientMock.empty,
          GameRepoMock.empty,
          GameDownloader.Impl.layer,
          MockClock.empty
        )
      },
      test(
        "when a valid user is prvided with the archive and archive must be downloaded but it fails should mark the process as failed"
      ) {
        val platform       = ChessPlatform.ChessDotCom
        val userName       = UserName("user")
        val user           = User(platform, userName)
        val userId         = UserId("https://api.chess.com/pub/player/tigran-c-137")
        val userIdentified = user.identified(userId)
        val taskId         = TaskId(UUID.randomUUID())
        val archiveId      = ArchiveId("https://example.com/archive/2022/1")
        val archiveContainsGamesTill =
          LocalDate.of(2022, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val now = LocalDate.of(2022, 2, 2).atStartOfDay(ZoneOffset.UTC).toInstant()

        val `get the archive` =
          val existingArchive = ArchiveResult(
            userId,
            archiveId,
            uri"https://example.com/archive/2022/1",
            archiveContainsGamesTill,
            Some(GameId("https://example.com/1")),
            2,
            ArchiveStatus.PartiallyDownloaded
          )
          ArchiveRepoMock.GetMethod(
            assertion = Assertion.equalTo((userId, archiveId)),
            result = Expectation.value(existingArchive)
          )

        val `download the archive` =
          ChessDotComClientMock.GamesMethod(
            assertion = Assertion.equalTo(uri"https://example.com/archive/2022/1"),
            result = Expectation.failure(ClientError.SomethingWentWrong)
          )

        val `update task status` = TaskRepoMock.FailureIncrement(
          assertion = Assertion.equalTo(taskId),
          result = Expectation.unit
        )

        val mock =
          (`get the archive` ++
            `download the archive` ++
            `update task status`).toLayer

        val testResult =
          for
            gameDownloader <- service
            actualResult   <- gameDownloader.download(userIdentified, archiveId, taskId)
            check = assertTrue(actualResult == ())
          yield check
        testResult.provide(mock, GameRepoMock.empty, GameDownloader.Impl.layer, MockClock.empty)
      },
      test(
        "when a valid user has been prvided with the archive, is has been downloaded but saving games has been failed should mark the process as failed"
      ) {
        val platform       = ChessPlatform.ChessDotCom
        val userName       = UserName("user")
        val user           = User(platform, userName)
        val userId         = UserId("https://api.chess.com/pub/player/tigran-c-137")
        val userIdentified = user.identified(userId)
        val taskId         = TaskId(UUID.randomUUID())
        val archiveId      = ArchiveId("https://example.com/archive/2022/1")
        val archiveContainsGamesTill =
          LocalDate.of(2022, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val now = LocalDate.of(2022, 2, 2).atStartOfDay(ZoneOffset.UTC).toInstant()

        val `get the archive` =
          val existingArchive = ArchiveResult(
            userId,
            archiveId,
            uri"https://example.com/archive/2022/1",
            archiveContainsGamesTill,
            None,
            0,
            ArchiveStatus.NotDownloaded
          )
          ArchiveRepoMock.GetMethod(
            assertion = Assertion.equalTo((userId, archiveId)),
            result = Expectation.value(existingArchive)
          )

        val `download the archive` =
          val downloadedGame1   = Game(uri"https://example.com/1", "Game1", 111L)
          val downloadedGame2   = Game(uri"https://example.com/2", "Game2", 222L)
          val downloadedArchive = Games(Seq(downloadedGame1, downloadedGame2))
          ChessDotComClientMock.GamesMethod(
            assertion = Assertion.equalTo(uri"https://example.com/archive/2022/1"),
            result = Expectation.value(downloadedArchive)
          )

        val `save downloaded games` =
          val historicalGame1 = HistoricalGame(uri"https://example.com/1", PgnStr("Game1"))
          val historicalGame2 = HistoricalGame(uri"https://example.com/2", PgnStr("Game2"))
          GameRepoMock.SaveGames(
            assertion = Assertion.equalTo((userId, Seq(historicalGame1, historicalGame2))),
            result = Expectation.failure(BrokenLogic.ServiceOverloaded)
          )

        val `update task status` = TaskRepoMock.FailureIncrement(
          assertion = Assertion.equalTo(taskId),
          result = Expectation.unit
        )

        val mock =
          (`get the archive` ++
            `download the archive` ++
            `save downloaded games` ++
            `update task status`).toLayer

        val testResult =
          for
            gameDownloader <- service
            actualResult   <- gameDownloader.download(userIdentified, archiveId, taskId)
            check = assertTrue(actualResult == ())
          yield check
        testResult.provide(mock, GameDownloader.Impl.layer, MockClock.empty)
      }
    )
  ) @@ TestAspect.sequential @@ MockReporter()
