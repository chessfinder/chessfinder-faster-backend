package chessfinder
package search.details

import client.*
import client.ClientError.*
import client.chess_com.ChessDotComClient
import client.chess_com.*
import persistence.core.DefaultDynamoDBExecutor
import persistence.{ GameRecord, PlatformType, UserRecord }
import search.*
import testkit.NarrowIntegrationSuite
import testkit.parser.JsonReader
import testkit.wiremock.ClientBackdoor
import util.{ RandomReadableString, UriParser }

import chess.format.pgn.PgnStr
import chessfinder.BrokenComputation
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

import scala.util.{ Success, Try }

object UserFetcherTest extends NarrowIntegrationSuite:

  val repo = ZIO.service[UserFetcher]
  def spec =
    suite("UserFetcher")(
      suite("get")(
        test("should get the user from the table if it exists") {

          val userName = UserName(RandomReadableString())
          val userId   = UserId(RandomReadableString())
          val user     = chessfinder.User(ChessPlatform.ChessDotCom, userName)
          val record =
            UserRecord(platform = PlatformType.CHESS_DOT_COM, user_name = userName, user_id = userId)
          val expectedResult = chessfinder.UserIdentified(ChessPlatform.ChessDotCom, userName, userId)

          for
            UserFetcher  <- repo
            _            <- UserRecord.Table.put(record)
            actualResult <- UserFetcher.get(user)
            result       <- assertTrue(actualResult == expectedResult)
          yield result
        },
        test("should give back ProfileIsNotCached if the user does not exists in the database") {
          val userName       = UserName(RandomReadableString())
          val userId         = UserId(RandomReadableString())
          val user           = chessfinder.User(ChessPlatform.ChessDotCom, userName)
          val expectedResult = BrokenComputation.ProfileIsNotCached(user)

          for
            UserFetcher  <- repo
            actualResult <- UserFetcher.get(user).either
            result       <- assertTrue(actualResult == Left(expectedResult))
          yield result
        }
      )
    ).provideLayer(dynamodbLayer >+> UserFetcher.Impl.layer) @@ TestAspect.sequential
