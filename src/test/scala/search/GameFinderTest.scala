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

import core.SearchFen
object GameFinderTest extends ZIOSpecDefault with Mocks:

  override def spec = suite("GameFinder")(
    test("when board is not valid should fail") {
      val board    = SearchFen("")
      val platform = ChessPlatform.ChessDotCom
      val userName = UserName("user")

      val boardValidatorLayer = BoardValidatorMock.Validate.apply(
        assertion = Assertion.equalTo(board),
        result = Expectation.failure(InvalidSearchBoard)
      ).toLayer

      val actualResult = GameFinder
        .find(board, platform, userName)
        .provide(boardValidatorLayer, SearcherMock.empty, GameDownloaderMock.empty, GameFinder.Impl.layer)

      assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(InvalidSearchBoard)))

    },
    test("when board is valid but user does not exist should return ProfileNotFound") {

      val board    = SearchFen("")
      val platform = ChessPlatform.ChessDotCom
      val userName     = UserName("user")
      val user = User(platform, userName)
      
      val boardValidatorLayer = BoardValidatorMock.Validate(
        assertion = Assertion.equalTo(board),
        result = Expectation.value(ProbabilisticBoard.empty)
      ).toLayer

      val gameDownloaderLayer = GameDownloaderMock.Downlaod(
        assertion = Assertion.equalTo(user),
        result = Expectation.failure(ProfileNotFound(userName))
      ).toLayer

      val actualResult = GameFinder
        .find(board, platform, userName)
        .provide(boardValidatorLayer, SearcherMock.empty, gameDownloaderLayer, GameFinder.Impl.layer)

      assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(ProfileNotFound(userName))))

    },
    test("when board is valid but user does not have any game method find should return NoGameAvaliable") {
      
      val board    = SearchFen("")
      val platform = ChessPlatform.ChessDotCom
      val userName     = UserName("user")
      val user = User(platform, userName)
      
      val boardValidatorLayer = BoardValidatorMock.Validate(
        assertion = Assertion.equalTo(board),
        result = Expectation.value(ProbabilisticBoard.empty)
      ).toLayer

      val gameDownloaderLayer = GameDownloaderMock.Downlaod(
        assertion = Assertion.equalTo(user),
        result = Expectation.failure(NoGameAvaliable(userName))
      ).toLayer

      val actualResult = GameFinder
        .find(board, platform, userName)
        .provide(boardValidatorLayer, SearcherMock.empty, gameDownloaderLayer, GameFinder.Impl.layer)
      
      assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(NoGameAvaliable(userName))))
    },
    test(
      "when board is valid and user exists and their all games are downloaded and parsed successfully then the method find should return the list of matched games with the DownloadStatus.Full"
    ) {
      val board    = SearchFen("")
      val platform = ChessPlatform.ChessDotCom
      val userName = UserName("user")
      val user = User(platform, userName)

      val searchBoard = ProbabilisticBoard.empty
      
      val boardValidatorLayer = BoardValidatorMock.Validate.apply(
        assertion = Assertion.equalTo(board),
        result = Expectation.value(searchBoard)
      ).toLayer


      val historicalGame1 = HistoricalGame(uri"https://example.com1", PgnStr("1"))
      val historicalGame2 = HistoricalGame(uri"https://example.com2", PgnStr("2"))
      val historicalGame3 = HistoricalGame(uri"https://example.com3", PgnStr("3"))

      val downloadingResult = DownloadingResult(
        Seq(
          historicalGame1,
          historicalGame2,
          historicalGame3
        ),
        Seq.empty
      )

      val gameDownloaderLayer = GameDownloaderMock.Downlaod(
        assertion = Assertion.equalTo(user),
        result = Expectation.value(downloadingResult)
      ).toLayer

      val searcherLayer = 
        val mock = 
          SearcherMock.Find(
            assertion = Assertion.equalTo((historicalGame1.png, searchBoard)),
            result = Expectation.value(true)
          ) ++ SearcherMock.Find(
            assertion = Assertion.equalTo((historicalGame2.png, searchBoard)),
            result = Expectation.value(true)
          ) ++ SearcherMock.Find(
            assertion = Assertion.equalTo((historicalGame3.png, searchBoard)),
            result = Expectation.value(true)
          )
        mock.toLayer

      val machedGame1 = MatchedGame(uri"https://example.com1")
      val machedGame2 = MatchedGame(uri"https://example.com2")
      val machedGame3 = MatchedGame(uri"https://example.com3")
      val machedGames = List(machedGame1, machedGame2, machedGame3)

      val expectedResult = SearchResult(machedGames, DownloadStatus.Full)

      val actualResult = GameFinder
        .find(board, platform, userName)
        .provide(boardValidatorLayer, searcherLayer, gameDownloaderLayer, GameFinder.Impl.layer)

      assertZIO(actualResult)(Assertion.equalTo(expectedResult))
    },
    test(
      "when board is valid and user exists and their all three games are downloaded but one of them is not parsed successfullty, the second one does not match then the method find should return the list of matched games with only third game and with the DownloadStatus.Partial"
    ) {
      val board    = SearchFen("")
      val platform = ChessPlatform.ChessDotCom
      val userName = UserName("user")
      val user = User(platform, userName)

      val searchBoard = ProbabilisticBoard.empty
      
      val boardValidatorLayer = BoardValidatorMock.Validate.apply(
        assertion = Assertion.equalTo(board),
        result = Expectation.value(searchBoard)
      ).toLayer


      val historicalGame1 = HistoricalGame(uri"https://example.com1", PgnStr("1"))
      val historicalGame2 = HistoricalGame(uri"https://example.com2", PgnStr("2"))
      val historicalGame3 = HistoricalGame(uri"https://example.com3", PgnStr("3"))

      val downloadingResult = DownloadingResult(
        Seq(
          historicalGame1,
          historicalGame2,
          historicalGame3
        ),
        Seq.empty
      )

      val gameDownloaderLayer = GameDownloaderMock.Downlaod(
        assertion = Assertion.equalTo(user),
        result = Expectation.value(downloadingResult)
      ).toLayer

      val searcherLayer = 
        val mock = 
          SearcherMock.Find(
            assertion = Assertion.equalTo((historicalGame1.png, searchBoard)),
            result = Expectation.value(true)
          ) ++ SearcherMock.Find(
            assertion = Assertion.equalTo((historicalGame2.png, searchBoard)),
            result = Expectation.value(false)
          ) ++ SearcherMock.Find(
            assertion = Assertion.equalTo((historicalGame3.png, searchBoard)),
            result = Expectation.failure(BrokenLogic.InvalidGame)
          )
        mock.toLayer

      val machedGame1 = MatchedGame(uri"https://example.com1")
      
      val machedGames = List(machedGame1)

      val expectedResult = SearchResult(machedGames, DownloadStatus.Partial)

      val actualResult = GameFinder
        .find(board, platform, userName)
        .provide(boardValidatorLayer, searcherLayer, gameDownloaderLayer, GameFinder.Impl.layer)

      assertZIO(actualResult)(Assertion.equalTo(expectedResult))
    },
    test(
      "when user exists and some of their games are downloaded, some of them are not, then the method find should return the list of matched games with the DownloadStatus.Partial"
    ) {
      val board    = SearchFen("")
      val platform = ChessPlatform.ChessDotCom
      val userName = UserName("user")
      val user = User(platform, userName)

      val searchBoard = ProbabilisticBoard.empty
      
      val boardValidatorLayer = BoardValidatorMock.Validate.apply(
        assertion = Assertion.equalTo(board),
        result = Expectation.value(searchBoard)
      ).toLayer


      val historicalGame1 = HistoricalGame(uri"https://example.com1", PgnStr("1"))
      val historicalGame2 = HistoricalGame(uri"https://example.com2", PgnStr("2"))

      val downloadingResult = DownloadingResult(
        Seq(
          historicalGame1,
          historicalGame2
        ),
        Seq(uri"https://example.com3")
      )

      val gameDownloaderLayer = GameDownloaderMock.Downlaod(
        assertion = Assertion.equalTo(user),
        result = Expectation.value(downloadingResult)
      ).toLayer

      val searcherLayer = 
        val mock = 
          SearcherMock.Find(
            assertion = Assertion.equalTo((historicalGame1.png, searchBoard)),
            result = Expectation.value(true)
          ) ++ SearcherMock.Find(
            assertion = Assertion.equalTo((historicalGame2.png, searchBoard)),
            result = Expectation.value(true)
          )
        mock.toLayer

      val machedGame1 = MatchedGame(uri"https://example.com1")
      val machedGame2 = MatchedGame(uri"https://example.com2")
      val machedGames = List(machedGame1, machedGame2)

      val expectedResult = SearchResult(machedGames, DownloadStatus.Partial)

      val actualResult = GameFinder
        .find(board, platform, userName)
        .provide(boardValidatorLayer, searcherLayer, gameDownloaderLayer, GameFinder.Impl.layer)

      assertZIO(actualResult)(Assertion.equalTo(expectedResult))

    }
  )
