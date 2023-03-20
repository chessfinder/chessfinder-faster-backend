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

import core.SearchFen
class GameDownloaderTest extends ZIOSpecDefault:

  val service = GameDownloader.Impl()

  override def spec = suite("GameDownloader")(
    test("when user exists and their archives are avalable then the method download should return the list of either the game or the exception") {
      val user = User(ChessPlatform.ChessDotCom, UserName("user"))

      val expectedGame1 = HistoricalGame(uri"https://example.com1", PgnStr(""))
      val expectedGame2 = HistoricalGame(uri"https://example.com2", PgnStr(""))
      val expectedGame3 = HistoricalGame(uri"https://example.com3", PgnStr(""))
      val expectedGames = List(expectedGame1, expectedGame2, expectedGame3)

      val expectedResult = DownloadingResult(expectedGames, Seq.empty)
    

      val actualResult = service.download(user)

      assertZIO(actualResult)(Assertion.equalTo(expectedResult))

    },
    
    test("when user does not exist should return ProfileNotFound") {
      val user = User(ChessPlatform.ChessDotCom, UserName("user"))

      
      val actualResult = service.download(user)

      assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(ProfileNotFound)))

    },

    test("when there is an archive which is not availabe should return a result with an information about the missing archive") {
      val user = User(ChessPlatform.ChessDotCom, UserName("user"))

      val expectedGame1 = HistoricalGame(uri"https://example.com/game1", PgnStr(""))
      val expectedGame2 = HistoricalGame(uri"https://example.com/game2", PgnStr(""))
      val expectedGames = List(expectedGame1, expectedGame2)

      val expectedResult = DownloadingResult(expectedGames, Seq(uri"https://example.com/game3"))
      
      val actualResult = service.download(user)

      assertZIO(actualResult)(Assertion.equalTo(expectedResult))

    },
  )
