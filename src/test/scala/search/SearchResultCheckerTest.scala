package chessfinder
package search

import BrokenComputation.*
import search.*

import zio.ZIO
import zio.mock.{ Expectation, MockClock, MockRandom }
import zio.test.*

import java.time.Instant
import java.util.UUID

object SearchResultCheckerTest extends ZIOSpecDefault with Mocks:

  val service = ZIO.service[SearchResultChecker]
  override def spec = suite("SearchResultChecker")(
    suite("check")(
      test(
        "when there is a SearchResult in the database should return it"
      ) {
        val searchRequestId = SearchRequestId(UUID.randomUUID())

        val searchResult = SearchResult(
          id = searchRequestId,
          startSearchAt = Instant.now(),
          lastExaminedAt = Instant.now(),
          examined = 7,
          total = 8,
          matched = Seq.empty[MatchedGame],
          status = SearchStatus.SearchedAll
        )

        val `getting the result` = SearchResultRepoMock.GetMethod(
          assertion = Assertion.equalTo(searchRequestId),
          result = Expectation.value(searchResult)
        )

        val mock = (
          `getting the result`
        ).toLayer

        val testResult =
          for
            gameFinder   <- service
            actualResult <- gameFinder.check(searchRequestId)
            check        <- assertTrue(actualResult == searchResult)
          yield check

        testResult.provide(
          mock,
          SearchResultChecker.Impl.layer
        )
      },
      test(
        "when there is no SearchResult in the database should return SearchResultNotFound"
      ) {
        val searchRequestId = SearchRequestId(UUID.randomUUID())

        val `getting the result` = SearchResultRepoMock.GetMethod(
          assertion = Assertion.equalTo(searchRequestId),
          result = Expectation.failure(SearchResultNotFound(searchRequestId))
        )

        val mock = (
          `getting the result`
        ).toLayer

        val testResult =
          for
            gameFinder   <- service
            actualResult <- gameFinder.check(searchRequestId).either
            check        <- assertTrue(actualResult == Left(SearchResultNotFound(searchRequestId)))
          yield check

        testResult.provide(
          mock,
          SearchResultChecker.Impl.layer
        )
      },
      test(
        "when there is an error should return ServiceOverloaded"
      ) {
        val searchRequestId = SearchRequestId(UUID.randomUUID())

        val `getting the result` = SearchResultRepoMock.GetMethod(
          assertion = Assertion.equalTo(searchRequestId),
          result = Expectation.failure(ServiceOverloaded)
        )

        val mock = (
          `getting the result`
        ).toLayer

        val testResult =
          for
            gameFinder   <- service
            actualResult <- gameFinder.check(searchRequestId).either
            check        <- assertTrue(actualResult == Left(ServiceOverloaded))
          yield check

        testResult.provide(
          mock,
          SearchResultChecker.Impl.layer
        )
      }
    )
  ) @@ TestAspect.sequential
