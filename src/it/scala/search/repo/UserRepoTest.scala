package chessfinder
package search.repo

import zio.test.*
import zio.*
import client.chess_com.ChessDotComClient
import chessfinder.testkit.wiremock.ClientBackdoor
import sttp.model.Uri
import client.chess_com.dto.*
import client.*
import client.ClientError.*
import search.entity.UserName
import scala.util.Success
import zio.http.Client
import sttp.model.Uri.UriContext
import com.typesafe.config.ConfigFactory
import scala.util.Try
import zio.ZLayer
import search.entity.*
import testkit.parser.JsonReader
import testkit.NarrowIntegrationSuite
import zio.aws.netty
import zio.aws.core.config.AwsConfig
import persistence.core.DefaultDynamoDBExecutor
import zio.dynamodb.*
import chessfinder.persistence.PlatformType
import chessfinder.persistence.UserRecord
import persistence.GameRecord
import util.UriParser
import chess.format.pgn.PgnStr
import io.circe.*
import chessfinder.util.RandomReadableString
import chessfinder.search.BrokenLogic

object UserRepoTest extends NarrowIntegrationSuite:

  def spec =
    suite("UserRepo")(
      suite("save")(
        test("should create new user in the database") {
          val userName       = UserName(RandomReadableString())
          val userId         = UserId(RandomReadableString())
          val user           = User(ChessPlatform.ChessDotCom, userName)
          val userIdentified = UserIdentified(ChessPlatform.ChessDotCom, userName, userId)

          val expectedResult =
            UserRecord(platform = PlatformType.CHESS_DOT_COM, user_name = userName, user_id = userId)

          for
            _            <- UserRepo.save(userIdentified)
            actualResult <- UserRecord.Table.get[UserRecord](userName, PlatformType.CHESS_DOT_COM)
            result       <- assertTrue(actualResult == Right(expectedResult))
          yield result
        }
      ),
      suite("get")(
        test("should get the user from the table if it exists") {

          val userName = UserName(RandomReadableString())
          val userId   = UserId(RandomReadableString())
          val user     = User(ChessPlatform.ChessDotCom, userName)
          val record =
            UserRecord(platform = PlatformType.CHESS_DOT_COM, user_name = userName, user_id = userId)
          val expectedResult = UserIdentified(ChessPlatform.ChessDotCom, userName, userId)

          for
            _            <- UserRecord.Table.put(record)
            actualResult <- UserRepo.get(user)
            result       <- assertTrue(actualResult == expectedResult)
          yield result
        },
        test("should give back ProfileIsNotCached if the user does not exists in the database") {
          val userName       = UserName(RandomReadableString())
          val userId         = UserId(RandomReadableString())
          val user           = User(ChessPlatform.ChessDotCom, userName)
          val expectedResult = BrokenLogic.ProfileIsNotCached(user)

          for
            actualResult <- UserRepo.get(user).either
            result       <- assertTrue(actualResult == Left(expectedResult))
          yield result
        }
      )
    ).provideLayer(dynamodbLayer >+> UserRepo.Impl.layer) @@ TestAspect.sequential
