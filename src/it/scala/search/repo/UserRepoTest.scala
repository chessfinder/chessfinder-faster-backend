package chessfinder
package search.repo

import client.*
import client.ClientError.*
import client.chess_com.ChessDotComClient
import client.chess_com.dto.*
import persistence.core.DefaultDynamoDBExecutor
import persistence.{ GameRecord, PlatformType, UserRecord }
import search.BrokenLogic
import search.entity.*
import testkit.NarrowIntegrationSuite
import testkit.parser.JsonReader
import testkit.wiremock.ClientBackdoor
import util.{ RandomReadableString, UriParser }

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

import scala.util.{ Success, Try }

object UserRepoTest extends NarrowIntegrationSuite:

  val repo = ZIO.service[UserRepo]
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
            userRepo     <- repo
            _            <- userRepo.save(userIdentified)
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
            userRepo     <- repo
            _            <- UserRecord.Table.put(record)
            actualResult <- userRepo.get(user)
            result       <- assertTrue(actualResult == expectedResult)
          yield result
        },
        test("should give back ProfileIsNotCached if the user does not exists in the database") {
          val userName       = UserName(RandomReadableString())
          val userId         = UserId(RandomReadableString())
          val user           = User(ChessPlatform.ChessDotCom, userName)
          val expectedResult = BrokenLogic.ProfileIsNotCached(user)

          for
            userRepo     <- repo
            actualResult <- userRepo.get(user).either
            result       <- assertTrue(actualResult == Left(expectedResult))
          yield result
        }
      )
    ).provideLayer(dynamodbLayer >+> UserRepo.Impl.layer) @@ TestAspect.sequential
