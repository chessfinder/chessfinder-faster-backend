package chessfinder
package search

import zio.test.*
import chessfinder.core.SearchFen
import chessfinder.core.ProbabilisticBoard
import search.BrokenLogic.*
import search.entity.*
import sttp.model.Uri.UriContext
import client.chess_com.dto.*
import chess.format.pgn.PgnStr
import zio.mock.Expectation
import chess.format.pgn.PgnStr

import core.SearchFen
import chessfinder.client.ClientError

object GameDownloaderTest extends ZIOSpecDefault with Mocks:

  override def spec = suite("GameDownloader")(
    test(
      "when user exists and their archives are avalable then the method download should return the list of games"
    ) {
      val user = User(ChessPlatform.ChessDotCom, UserName("user"))

      val expectedArchive =
        Archives(Seq(uri"https://example.com/archive/1", uri"https://example.com/archive/2"))
      val getArchiveCall = ChessDotComClientMock.ArchivesMethod(
        assertion = Assertion.equalTo(UserName("user")),
        result = Expectation.value(expectedArchive)
      )

      val expectedDownloadedGame1  = Game(uri"https://example.com/1", "Game1")
      val expectedDownloadedGame2  = Game(uri"https://example.com/2", "Game2")
      val expectedDownloadedGames1 = Games(Seq(expectedDownloadedGame1, expectedDownloadedGame2))

      val downloadFirstArchive = ChessDotComClientMock.GamesMethod(
        assertion = Assertion.equalTo(uri"https://example.com/archive/1"),
        result = Expectation.value(expectedDownloadedGames1)
      )

      val expectedDownloadedGame3  = Game(uri"https://example.com/3", "Game3")
      val expectedDownloadedGames2 = Seq(expectedDownloadedGame3)

      val downloadSecondArchive = ChessDotComClientMock.GamesMethod(
        assertion = Assertion.equalTo(uri"https://example.com/archive/2"),
        result = Expectation.value(Games(Seq(expectedDownloadedGame3)))
      )

      val mock = (getArchiveCall ++ downloadFirstArchive ++ downloadSecondArchive).toLayer

      val expectedGame1 = HistoricalGame(uri"https://example.com/1", PgnStr("Game1"))
      val expectedGame2 = HistoricalGame(uri"https://example.com/2", PgnStr("Game2"))
      val expectedGame3 = HistoricalGame(uri"https://example.com/3", PgnStr("Game3"))
      val expectedGames = List(expectedGame1, expectedGame2, expectedGame3)

      val expectedResult = DownloadingResult(expectedGames, List.empty)

      val actualResult = GameDownloader
        .download(user)
        .provide(mock, GameDownloader.Impl.layer)

      assertZIO(actualResult)(Assertion.equalTo(expectedResult))

    },
    test(
      "when there is an archive which is not availabe should return a result with an information about the missing archive"
    ) {
      val user = User(ChessPlatform.ChessDotCom, UserName("user"))

      val expectedArchive =
        Archives(Seq(uri"https://example.com/archive/1", uri"https://example.com/archive/2"))
      val getArchiveCall = ChessDotComClientMock.ArchivesMethod(
        assertion = Assertion.equalTo(UserName("user")),
        result = Expectation.value(expectedArchive)
      )

      val expectedDownloadedGame1  = Game(uri"https://example.com/1", "Game1")
      val expectedDownloadedGame2  = Game(uri"https://example.com/2", "Game2")
      val expectedDownloadedGames1 = Games(Seq(expectedDownloadedGame1, expectedDownloadedGame2))

      val downloadFirstArchive = ChessDotComClientMock.GamesMethod(
        assertion = Assertion.equalTo(uri"https://example.com/archive/1"),
        result = Expectation.value(expectedDownloadedGames1)
      )

      val downloadSecondArchive = ChessDotComClientMock.GamesMethod(
        assertion = Assertion.equalTo(uri"https://example.com/archive/2"),
        result = Expectation.failure(ClientError.SomethingWentWrong)
      )

      val mock = (getArchiveCall ++ downloadFirstArchive ++ downloadSecondArchive).toLayer

      val expectedGame1 = HistoricalGame(uri"https://example.com/1", PgnStr("Game1"))
      val expectedGame2 = HistoricalGame(uri"https://example.com/2", PgnStr("Game2"))
      val expectedGames = List(expectedGame1, expectedGame2)

      val expectedResult = DownloadingResult(expectedGames, List(uri"https://example.com/archive/2"))

      val actualResult = GameDownloader
        .download(user)
        .provide(mock, GameDownloader.Impl.layer)

      assertZIO(actualResult)(Assertion.equalTo(expectedResult))

    },
    test("when user does not exist should return ProfileNotFound") {
      val userName = UserName("user")
      val user     = User(ChessPlatform.ChessDotCom, userName)

      val expectedArchive =
        Archives(Seq(uri"https://example.com/archive/1", uri"https://example.com/archive/2"))
      val getArchiveCall = ChessDotComClientMock.ArchivesMethod(
        assertion = Assertion.equalTo(userName),
        result = Expectation.failure(ClientError.ProfileNotFound(userName))
      )

      val mock = (getArchiveCall).toLayer

      val actualResult = GameDownloader
        .download(user)
        .provide(mock, GameDownloader.Impl.layer)

      assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(ProfileNotFound(userName))))

    },
    test("when user does not have any archive should return NoGameAvaliable") {
      val userName = UserName("user")
      val user = User(ChessPlatform.ChessDotCom, userName)

      val expectedArchive = Archives(Seq.empty)
      val getArchiveCall = ChessDotComClientMock.ArchivesMethod(
        assertion = Assertion.equalTo(UserName("user")),
        result = Expectation.value(expectedArchive)
      )

      val mock = (getArchiveCall).toLayer

      val actualResult = GameDownloader
        .download(user)
        .provide(mock, GameDownloader.Impl.layer)

      assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(NoGameAvaliable(userName))))
    }
  )
