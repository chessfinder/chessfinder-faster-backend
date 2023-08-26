package chessfinder
package search.repo

import client.*
import client.ClientError.*
import client.chess_com.ChessDotComClient
import client.chess_com.dto.*
import persistence.*
import persistence.core.DefaultDynamoDBExecutor
import search.entity.*
import testkit.NarrowIntegrationSuite
import testkit.parser.JsonReader
import testkit.wiremock.ClientBackdoor
import util.{ RandomReadableString, UriParser }

import cats.effect.kernel.syntax.resource
import chess.format.pgn.PgnStr
import com.typesafe.config.ConfigFactory
import io.circe.*
import sttp.model.Uri
import sttp.model.Uri.UriContext
import zio.*
import zio.aws.core.config.AwsConfig
import zio.aws.netty
import zio.dynamodb.*
import zio.http.Client
import zio.test.*

import java.time.*
import java.util.UUID
import scala.util.{ Success, Try }

object SearchResultRepoTest extends NarrowIntegrationSuite:
  val repo = ZIO.service[SearchResultRepo]
  def spec =
    suite("SearchResultRepo")(
      suite("get")(
        test("should return the SearchResult from the Database") {

          val searchRequestId = SearchRequestId(UUID.randomUUID())
          val startSearchAt   = Instant.now()
          val lastExaminedAt  = Instant.now()
          val examined        = 10
          val total           = 100
          val matched = Set(
            uri"https://example.com/pub/player/userName/games/2022/10",
            uri"https://example.com/pub/player/userName/games/2022/11"
          )

          val searchResultRecord = SearchResultRecord(
            search_request_id = searchRequestId,
            start_search_at = startSearchAt,
            last_examined_at = lastExaminedAt,
            examined = examined,
            total = total,
            matched = matched,
            status = SearchStatus.SearchedPartially
          )

          val expectedResult = SearchResult(
            id = searchRequestId,
            startSearchAt = startSearchAt,
            lastExaminedAt = lastExaminedAt,
            examined = examined,
            total = total,
            matched = matched.map(MatchedGame.apply).toSeq,
            status = SearchStatus.SearchedPartially
          )

          for
            searchResultRepo <- repo
            _                <- SearchResultRecord.Table.put(searchResultRecord)
            actualResult     <- searchResultRepo.get(searchRequestId)
            result           <- assertTrue(actualResult == expectedResult)
          yield result
        }
      ),
      suite("initiate")(
        test("should create a new record in the table") {

          val searchRequestId = SearchRequestId(UUID.randomUUID())
          val startSearchAt   = Instant.now()
          val total           = 100

          val expectedResult = SearchResultRecord(
            search_request_id = searchRequestId,
            start_search_at = startSearchAt,
            last_examined_at = startSearchAt,
            examined = 0,
            total = total,
            matched = Set.empty,
            status = SearchStatus.InProgress
          )

          for
            searchResultRepo <- repo
            returnedValue    <- searchResultRepo.initiate(searchRequestId, startSearchAt, total)
            actualResult     <- SearchResultRecord.Table.get[SearchResultRecord](searchRequestId)
            result1          <- assertTrue(actualResult == Right(expectedResult))
            result2          <- assertTrue(actualResult.map(_.toSearchResult) == Right(returnedValue))
          yield result1 && result2
        }
      ),
      suite("update")(
        test("should update the exisitng recornd in the table") {

          val searchRequestId = SearchRequestId(UUID.randomUUID())
          val startSearchAt   = Instant.now()
          val lastExaminedAt  = Instant.now()
          val examined        = 10
          val total           = 100
          val matched = Set(
            uri"https://example.com/pub/player/userName/games/2022/10",
            uri"https://example.com/pub/player/userName/games/2022/11"
          )

          val oldRecord = SearchResultRecord(
            search_request_id = searchRequestId,
            start_search_at = startSearchAt,
            last_examined_at = startSearchAt,
            examined = 0,
            total = total,
            matched = Set.empty,
            status = SearchStatus.SearchedPartially
          )

          val newSearchResult = SearchResult(
            id = searchRequestId,
            startSearchAt = startSearchAt,
            lastExaminedAt = lastExaminedAt,
            examined = examined,
            total = total,
            matched = matched.map(MatchedGame.apply).toSeq,
            status = SearchStatus.SearchedAll
          )

          val expectedResult = SearchResultRecord(
            search_request_id = searchRequestId,
            start_search_at = startSearchAt,
            last_examined_at = lastExaminedAt,
            examined = examined,
            total = total,
            matched = matched,
            status = SearchStatus.SearchedAll
          )

          for
            searchResultRepo <- repo
            _                <- SearchResultRecord.Table.put(oldRecord)
            _                <- searchResultRepo.update(newSearchResult)
            actualResult     <- SearchResultRecord.Table.get[SearchResultRecord](searchRequestId)
            result           <- assertTrue(actualResult == Right(expectedResult))
          yield result
        }
      )
    ).provideLayer(dynamodbLayer >+> SearchResultRepo.Impl.layer) @@ TestAspect.sequential
