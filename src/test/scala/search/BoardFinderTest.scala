package chessfinder
package search

import BrokenComputation.*
import core.{ ProbabilisticBoard, SearchFen }
import search.*

import chess.format.pgn.PgnStr
import sttp.model.Uri.UriContext
import zio.ZIO
import zio.mock.{ Expectation, MockClock }
import zio.test.*

import java.time.Instant
import java.util.UUID

object BoardFinderTest extends ZIOSpecDefault with Mocks:

  val service = ZIO.service[BoardFinder]
  override def spec = suite("BoardFinder")(
    suite("find")(
      test(
        "when there is SearchRequest should fetch games from the database and search the game"
      ) {
        val searchRequestId = SearchRequestId(UUID.randomUUID())

        val board  = SearchFen("")
        val userId = UserId("userId")

        val searchBoard    = ProbabilisticBoard.empty
        val startSearchAt  = Instant.now()
        val lastExaminedAt = Instant.now()

        val `validating the board` = BoardValidatorMock.Validate(
          assertion = Assertion.equalTo(board),
          result = Expectation.value(searchBoard)
        )

        val `getting SearchResult from the database` =
          val searchRequest = SearchResult(
            id = searchRequestId,
            startSearchAt = startSearchAt,
            total = 10
          )
          SearchResultRepoMock.GetMethod(
            assertion = Assertion.equalTo(searchRequestId),
            result = Expectation.value(searchRequest)
          )

        val historicalGame1 = HistoricalGame(uri"https://example.com1", PgnStr("1"))
        val historicalGame2 = HistoricalGame(uri"https://example.com2", PgnStr("2"))
        val historicalGame3 = HistoricalGame(uri"https://example.com3", PgnStr("3"))

        val `getting user's games from database` = GameFetcherMock.ListGames(
          assertion = Assertion.equalTo(userId),
          result = Expectation.value(Seq(historicalGame1, historicalGame2, historicalGame3))
        )

        val `looking through the games` =
          SearchFacadeAdapterMock.Find(
            assertion = Assertion.equalTo((historicalGame1.pgn, searchBoard)),
            result = Expectation.value(true)
          ) ++ SearchFacadeAdapterMock.Find(
            assertion = Assertion.equalTo((historicalGame2.pgn, searchBoard)),
            result = Expectation.value(false)
          ) ++ SearchFacadeAdapterMock.Find(
            assertion = Assertion.equalTo((historicalGame3.pgn, searchBoard)),
            result = Expectation.value(true)
          )

        val `checking the time` = MockClock.Instant(
          Expectation.value(lastExaminedAt)
        )

        val searchRequest = SearchResult(
          searchRequestId = searchRequestId,
          startSearchAt = startSearchAt,
          lastExaminedAt = lastExaminedAt,
          examined = 3,
          matched = Seq(uri"https://example.com1", uri"https://example.com3").map(MatchedGame.apply),
          status = SearchStatus.SearchedPartially,
          total = 10
        )

        val `updating SearchResult in the table` =
          SearchResultRepoMock.UpdateMethod(
            assertion = Assertion.equalTo(searchRequest),
            result = Expectation.unit
          )

        val mock = (
          `validating the board` ++
            `getting SearchResult from the database` ++
            `getting user's games from database` ++
            `looking through the games` ++
            `checking the time` ++
            `updating SearchResult in the table`
        ).toLayer

        val testResult =
          for
            gameFinder   <- service
            actualResult <- gameFinder.find(board, userId, searchRequestId)
            check        <- assertTrue(actualResult == ())
          yield check
        testResult.provide(mock, BoardFinder.Impl.layer)
      }
    )
  ) @@ TestAspect.sequential @@ zio.mock.MockReporter()
