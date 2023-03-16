package chessfinder
package search

import zio.test.*
import chessfinder.core.format.SearchFenReader
import chessfinder.core.format.SearchFen
import chessfinder.core.ProbabilisticBoard
import search.BrokenLogic.*
import search.entity.*
import sttp.model.Uri.UriContext
import client.chess_com.dto.* 
import chess.format.pgn.PgnStr

class GameFinderTest extends ZIOSpecDefault:

  val service = GameFinder.Impl()

  override def spec = suite("GameFinder")(
    test("when user exists and their all games are downloaded then the method find should return the list of matched games with the DownloadStatus.Full") {
      val board = SearchFen("")
      val platform = ChessPlatform.ChessDotCom
      val userName = UserName("user")

      val machedGame1 = MatchedGame(uri"https://example.com1")
      val machedGame2 = MatchedGame(uri"https://example.com2")
      val machedGame3 = MatchedGame(uri"https://example.com3")
      val machedGames = List(machedGame1, machedGame2, machedGame3)

      val expectedResult = SearchResult(machedGames, DownloadStatus.Full)
    
      val actualResult = service.find(board, platform, userName)

      assertZIO(actualResult)(Assertion.equalTo(expectedResult))

    },
    
    test("when user exists and some of their games are downloaded, some of them are not, then the method find should return the list of matched games with the DownloadStatus.Partial") {
      val board = SearchFen("")
      val platform = ChessPlatform.ChessDotCom
      val userName = UserName("user")

      val machedGame1 = MatchedGame(uri"https://example.com1")
      val machedGame2 = MatchedGame(uri"https://example.com2")
      val machedGame3 = MatchedGame(uri"https://example.com3")
      val machedGames = List(machedGame1, machedGame2, machedGame3)

      val expectedResult = SearchResult(machedGames, DownloadStatus.Partial)
    
      val actualResult = service.find(board, platform, userName)

      assertZIO(actualResult)(Assertion.equalTo(expectedResult))

    },
    
    test("when user does not have any game method find should return NoGameAvaliable") {
      val board = SearchFen("")
      val platform = ChessPlatform.ChessDotCom
      val userName = UserName("user")

      val actualResult = service.find(board, platform, userName)

      assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(NoGameAvaliable(userName))))

    },
    
    test("when user does not exist should return ProfileNotFound") {
      val board = SearchFen("")
      val platform = ChessPlatform.ChessDotCom
      val user = UserName("user")

      val actualResult = service.find(board, platform, user)

      assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(ProfileNotFound)))

    }

  )
