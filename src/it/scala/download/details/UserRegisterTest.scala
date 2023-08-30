package chessfinder
package download.details

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

object UserRegisterTest extends NarrowIntegrationSuite:

  val repo = ZIO.service[UserRegister]
  def spec =
    suite("UserRegister")(
      suite("save")(
        test("should create new user in the database") {
          val userName       = UserName(RandomReadableString())
          val userId         = UserId(RandomReadableString())
          val user           = chessfinder.User(ChessPlatform.ChessDotCom, userName)
          val userIdentified = chessfinder.UserIdentified(ChessPlatform.ChessDotCom, userName, userId)

          val expectedResult =
            UserRecord(platform = PlatformType.CHESS_DOT_COM, user_name = userName, user_id = userId)

          for
            UserRegister <- repo
            _            <- UserRegister.save(userIdentified)
            actualResult <- UserRecord.Table.get[UserRecord](userName, PlatformType.CHESS_DOT_COM)
            result       <- assertTrue(actualResult == Right(expectedResult))
          yield result
        }
      )
    ).provideLayer(dynamodbLayer >+> UserRegister.Impl.layer) @@ TestAspect.sequential
