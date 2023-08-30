package chessfinder
package download.details

import client.*
import client.ClientError.*
import client.chess_com.{ ChessDotComClient, Games }
import persistence.core.DefaultDynamoDBExecutor
import persistence.{ GameRecord, PlatformType, UserRecord }
import search.*
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

object GameSaverTest extends NarrowIntegrationSuite:

  val repo = ZIO.service[GameSaver]

  def spec =
    suite("GameSaver")(
      suite("save")(
        test(
          "should put all game into database even if there are more than 25 games in the query (this ia a limitation of the dynamodb, we should overcome that using streams under the hood)"
        ) {

          val userId = UserId(RandomReadableString())

          val games = ZIO.attempt {
            val gamesAsJson = JsonReader.parseResource("samples/2022-11.json")
            val games       = Decoder[Games].decodeJson(gamesAsJson).toTry.get
            games.games.map(game => HistoricalGame(game.url, PgnStr(game.pgn))).toSet
          }

          for
            gameRepo       <- repo
            expectedResult <- games
            _              <- gameRepo.save(userId, expectedResult.toSeq)
            actualResult   <- GameRecord.Table.list[GameRecord](userId).map(_.map(_.toGame).toSet)
            result1        <- assertTrue(actualResult.toSet == expectedResult)
          yield result1
        }
      )
    ).provideLayer(dynamodbLayer >+> GameSaver.Impl.layer) @@ TestAspect.sequential
