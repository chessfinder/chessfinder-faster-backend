package chessfinder
package search

import client.chess_com.dto.*
import core.{ ProbabilisticBoard, SearchFen }
import search.BrokenLogic.*
import search.entity.*
import sttp.model.Uri.UriContext

import chess.format.pgn.PgnStr
import zio.ZIO
import zio.mock.{ Expectation, MockClock, MockRandom }
import zio.test.*
import sttp.model.Uri.UriContext
import java.time.Instant
import java.util.UUID

object SearchRequestAcceptorTest extends ZIOSpecDefault with Mocks:

  val service = ZIO.service[SearchRequestAcceptor]
  override def spec = suite("SearchRequestAcceptor")(
    suite("register")(
      test("when board is not valid should fail") {
        val board    = SearchFen("")
        val platform = ChessPlatform.ChessDotCom
        val userName = UserName("user")

        val `validating the board` = BoardValidatorMock.Validate(
          assertion = Assertion.equalTo(board),
          result = Expectation.failure(InvalidSearchBoard)
        )

        val mock = `validating the board`.toLayer

        val testResult =
          for
            gameFinder   <- service
            actualResult <- gameFinder.register(board, platform, userName).either
            check        <- assertTrue(actualResult == Left(InvalidSearchBoard))
          yield check

        testResult
          .provide(
            mock,
            UserRepoMock.empty,
            BoardSearchingProducerMock.empty,
            ArchiveRepoMock.empty,
            SearchResultRepoMock.empty,
            MockRandom.empty,
            MockClock.empty,
            SearchRequestAcceptor.Impl.layer
          )
      },
      test("when board is valid but user does not exist should return ProfileNotFound") {

        val board    = SearchFen("")
        val platform = ChessPlatform.ChessDotCom
        val userName = UserName("user")
        val user     = User(platform, userName)

        val `validating the board` = BoardValidatorMock.Validate(
          assertion = Assertion.equalTo(board),
          result = Expectation.value(ProbabilisticBoard.empty)
        )

        val `checking if user exists in database` = UserRepoMock.GetUser(
          assertion = Assertion.equalTo(user),
          result = Expectation.failure(ProfileNotFound(user))
        )

        val mock = (`validating the board` ++ `checking if user exists in database`).toLayer

        val testResult =
          for
            gameFinder   <- service
            actualResult <- gameFinder.register(board, platform, userName).either
            check        <- assertTrue(actualResult == Left(ProfileNotFound(user)))
          yield check
        testResult.provide(
          mock,
          BoardSearchingProducerMock.empty,
          ArchiveRepoMock.empty,
          SearchResultRepoMock.empty,
          MockRandom.empty,
          MockClock.empty,
          SearchRequestAcceptor.Impl.layer
        )
      },
      test("when board is valid, the user exists but does not any games should return NoGameAvailable") {

        val board          = SearchFen("")
        val platform       = ChessPlatform.ChessDotCom
        val userName       = UserName("user")
        val user           = User(platform, userName)
        val userId         = UserId("userId")
        val userIdentified = user.identified(userId)

        val `validating the board` = BoardValidatorMock.Validate(
          assertion = Assertion.equalTo(board),
          result = Expectation.value(ProbabilisticBoard.empty)
        )

        val `checking if user exists in database` = UserRepoMock.GetUser(
          assertion = Assertion.equalTo(user),
          result = Expectation.value(userIdentified)
        )

        val `getting user's archives from database` =
          ArchiveRepoMock.GetAllMethod(
            assertion = Assertion.equalTo(userId),
            result = Expectation.value(Seq.empty[ArchiveResult])
          )

        val mock = (
          `validating the board` ++ `checking if user exists in database` ++ `getting user's archives from database`
        ).toLayer

        val testResult =
          for
            gameFinder   <- service
            actualResult <- gameFinder.register(board, platform, userName).either
            check        <- assertTrue(actualResult == Left(NoGameAvailable(user)))
          yield check

        testResult.provide(
          mock,
          BoardSearchingProducerMock.empty,
          SearchResultRepoMock.empty,
          MockRandom.empty,
          MockClock.empty,
          SearchRequestAcceptor.Impl.layer
        )
      },
      test("when board is valid, the user exists and has games should send SearchBoarCommand to queue") {

        val searchRequestId = SearchRequestId(UUID.randomUUID())
        val board           = SearchFen("")
        val platform        = ChessPlatform.ChessDotCom
        val userName        = UserName("user")
        val user            = User(platform, userName)
        val userId          = UserId("userId")
        val userIdentified  = user.identified(userId)

        val `validating the board` = BoardValidatorMock.Validate(
          assertion = Assertion.equalTo(board),
          result = Expectation.value(ProbabilisticBoard.empty)
        )

        val `checking if user exists in database` = UserRepoMock.GetUser(
          assertion = Assertion.equalTo(user),
          result = Expectation.value(userIdentified)
        )

        val `getting user's archives from database` =

          val archiveResult1 = ArchiveResult(
            userId = userId,
            archiveId = ArchiveId("archive1"),
            resource = uri"https://example.com/archive/2022/1",
            till = Instant.now(),
            lastGamePlayed = None,
            downloaded = 7,
            status = ArchiveStatus.FullyDownloaded
          )

          val archiveResult2 = ArchiveResult(
            userId = userId,
            archiveId = ArchiveId("archive2"),
            resource = uri"https://example.com/archive/2022/1",
            till = Instant.now(),
            lastGamePlayed = None,
            downloaded = 12,
            status = ArchiveStatus.FullyDownloaded
          )

          ArchiveRepoMock.GetAllMethod(
            assertion = Assertion.equalTo(userId),
            result = Expectation.value(Seq(archiveResult1, archiveResult2))
          )

        val now = Instant.now()

        val `checking the time` = MockClock.Instant(
          Expectation.value(now)
        )

        val `generating a SearchRequestId` =
          MockRandom.NextUUID(
            Expectation.value(searchRequestId.value)
          )

        val searchResult = SearchResult(searchRequestId, now, 19)
        val `registering SearchResult into database` =
          SearchResultRepoMock.InitiateMethod(
            assertion = Assertion.equalTo((searchRequestId, now, 19)),
            result = Expectation.value(searchResult)
          )

        val `sending SearchBoardCommand into the queue` =
          BoardSearchingProducerMock.PublishMethod(
            assertion = Assertion.equalTo((userIdentified, board, searchRequestId)),
            result = Expectation.unit
          )

        val mock = (
          `validating the board` ++
            `checking if user exists in database` ++
            `getting user's archives from database` ++
            `checking the time` ++
            `generating a SearchRequestId` ++
            `registering SearchResult into database` ++
            `sending SearchBoardCommand into the queue`
        ).toLayer

        val testResult =
          for
            gameFinder   <- service
            actualResult <- gameFinder.register(board, platform, userName)
            check        <- assertTrue(actualResult == searchResult)
          yield check

        testResult.provide(
          mock,
          SearchRequestAcceptor.Impl.layer
        )
      }
    ),
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
          BoardValidatorMock.empty,
          BoardSearchingProducerMock.empty,
          UserRepoMock.empty,
          ArchiveRepoMock.empty,
          MockRandom.empty,
          MockClock.empty,
          SearchRequestAcceptor.Impl.layer
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
          BoardValidatorMock.empty,
          BoardSearchingProducerMock.empty,
          UserRepoMock.empty,
          ArchiveRepoMock.empty,
          MockRandom.empty,
          MockClock.empty,
          SearchRequestAcceptor.Impl.layer
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
          BoardValidatorMock.empty,
          BoardSearchingProducerMock.empty,
          UserRepoMock.empty,
          ArchiveRepoMock.empty,
          MockRandom.empty,
          MockClock.empty,
          SearchRequestAcceptor.Impl.layer
        )
      }
    )
  ) @@ TestAspect.sequential
