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
import api.ApiVersion
import core.SearchFen
import chessfinder.client.ClientError
import chessfinder.search.repo.UserRepo

object GameFetcherTest extends ZIOSpecDefault with Mocks:

  override def spec =
    suite("GameFetcher")(
      suite("Impl")(
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

          val expectedResult = FetchingResult(expectedGames, List.empty)

          val actualResult = GameFetcher
            .fetch[ApiVersion.Newborn.type](user)
            .provide(mock, GameFetcher.Impl.layer)

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

          val expectedResult = FetchingResult(expectedGames, List(uri"https://example.com/archive/2"))

          val actualResult = GameFetcher
            .fetch[ApiVersion.Newborn.type](user)
            .provide(mock, GameFetcher.Impl.layer)

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

          val actualResult = GameFetcher
            .fetch[ApiVersion.Newborn.type](user)
            .provide(mock, GameFetcher.Impl.layer)

          assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(ProfileNotFound(user))))

        },
        test("when user does not have any archive should return NoGameAvaliable") {
          val userName = UserName("user")
          val user     = User(ChessPlatform.ChessDotCom, userName)

          val expectedArchive = Archives(Seq.empty)
          val getArchiveCall = ChessDotComClientMock.ArchivesMethod(
            assertion = Assertion.equalTo(UserName("user")),
            result = Expectation.value(expectedArchive)
          )

          val mock = (getArchiveCall).toLayer

          val actualResult = GameFetcher
            .fetch[ApiVersion.Newborn.type](user)
            .provide(mock, GameFetcher.Impl.layer)

          assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(NoGameAvaliable(user))))
        }
      ),
      suite("Local")(
        test(
          "when user is cached and their have at least one game then method download should return the list of games"
        ) {
          val userName               = UserName("user")
          val user                   = User(ChessPlatform.ChessDotCom, userName)
          val userId                 = UserId("userId")
          val expectedUserIdentified = UserIdentified(ChessPlatform.ChessDotCom, userName, userId)

          val getUser = UserRepoMock.GetUser(
            assertion = Assertion.equalTo(user),
            result = Expectation.value(expectedUserIdentified)
          )

          val expectedFetchedGame1 = HistoricalGame(uri"https://example.com/1", PgnStr("Game1"))
          val expectedFetchedGame2 = HistoricalGame(uri"https://example.com/2", PgnStr("Game2"))
          val expectedFetchedGames = Seq(expectedFetchedGame1, expectedFetchedGame2)

          val fetchGames = GameRepoMock.ListGames(
            assertion = Assertion.equalTo(expectedUserIdentified),
            result = Expectation.value(expectedFetchedGames)
          )

          val mock = (getUser ++ fetchGames).toLayer

          val expectedResult = FetchingResult(expectedFetchedGames, List.empty)

          val actualResult = GameFetcher
            .fetch[ApiVersion.Async.type](user)
            .provide(mock, GameFetcher.Local.layer)

          assertZIO(actualResult)(Assertion.equalTo(expectedResult))
        },
        test("when user does not exist should return ProfileNotFound") {
          val userName = UserName("user")
          val user     = User(ChessPlatform.ChessDotCom, userName)

          val expectedUserId = UserId("userId")

          val getUser = UserRepoMock.GetUser(
            assertion = Assertion.equalTo(user),
            result = Expectation.failure(BrokenLogic.ProfileNotFound(user))
          )

          val mock = (getUser).toLayer

          val actualResult = GameFetcher
            .fetch[ApiVersion.Async.type](user)
            .provide(mock, GameRepoMock.empty, GameFetcher.Local.layer)

          assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(ProfileNotFound(user))))
        },
        test("when user does not have any game should return NoGameAvaliable") {
          val userName               = UserName("user")
          val user                   = User(ChessPlatform.ChessDotCom, userName)
          val userId                 = UserId("userId")
          val expectedUserIdentified = UserIdentified(ChessPlatform.ChessDotCom, userName, userId)

          val getUser = UserRepoMock.GetUser(
            assertion = Assertion.equalTo(user),
            result = Expectation.value(expectedUserIdentified)
          )

          val fetchGames = GameRepoMock.ListGames(
            assertion = Assertion.equalTo(expectedUserIdentified),
            result = Expectation.failure(BrokenLogic.NoGameAvaliable(user))
          )

          val mock = (getUser ++ fetchGames).toLayer

          val actualResult = GameFetcher
            .fetch[ApiVersion.Async.type](user)
            .provide(mock, GameFetcher.Local.layer)

          assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(NoGameAvaliable(user))))
        }
      )
    ) @@ TestAspect.sequential
