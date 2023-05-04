package chessfinder
package flow

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
import zio.http.service.{ ChannelFactory, EventLoopGroup }
import zio.*
import testkit.BroadIntegrationSuite
import zio.http.Body
import client.ClientExt.*
import api.FindResponse
import zio.http.Client
import io.circe.*
import io.circe.parser
import scala.io.Source
import testkit.parser.JsonReader
import zio.dynamodb.*
import zio.aws.netty
import zio.aws.core.config.AwsConfig
import chessfinder.search.entity.HistoricalGame
import com.typesafe.config.{ Config, ConfigFactory }
import chessfinder.util.RandomReadableString
import persistence.core.DefaultDynamoDBExecutor
import chess.format.pgn.PgnStr
import chessfinder.persistence.GameRecord
import chessfinder.search.entity.UserId
import chessfinder.persistence.UserRecord
import chessfinder.search.entity.UserIdentified
import chessfinder.search.entity.ChessPlatform
import zio.http.model.Method

object FindGameSpec extends BroadIntegrationSuite:

  protected lazy val `chess.com` = ClientBackdoor("/chess_com")
  protected lazy val clientLayer = Client.default.orDie

  private lazy val dynamodbLayer: TaskLayer[DynamoDBExecutor] =
    val in = ((netty.NettyHttpClient.default >+> AwsConfig.default) ++ configLayer)
    in >>> DefaultDynamoDBExecutor.layer

  def spec =
    suite("Chessfinder when the game is provided")(
      test("and everything is OK, should find the game") {
        val userName = RandomReadableString()
        val userId   = UserId(RandomReadableString())
        val user     = UserIdentified(ChessPlatform.ChessDotCom, UserName(userName), userId)

        val createUser = UserRecord.Table.put(UserRecord.fromUserIdentified(user))

        def readGames(resource: String) = ZIO.attempt {
          val gamesAsJson = JsonReader.parseResource(resource)
          val games       = Decoder[Games].decodeJson(gamesAsJson).toTry.get
          games.games
            .map(game => HistoricalGame(game.url, PgnStr(game.pgn)))
            .map(game => GameRecord.fromGame(userId, game))
        }

        def fillDate(games: Seq[GameRecord]) =
          GameRecord.Table.putMany(games*)

        val `2022-07` = readGames(s"samples/2022-07.json").flatMap(fillDate)

        val `2022-08` = readGames(s"samples/2022-08.json").flatMap(fillDate)

        val `2022-09` = readGames(s"samples/2022-09.json").flatMap(fillDate)

        val `2022-10` = readGames(s"samples/2022-10.json").flatMap(fillDate)

        val `2022-11` = readGames(s"samples/2022-11.json").flatMap(fillDate)

        val expectedResult = FindResponse(
          resources = Seq(uri"https://www.chess.com/game/live/63025767719"),
          message = "All games are successfully analized."
        )

        val findRequest =
          val body = Body.fromString(
            s"""|{
                |  "user":"${userName}",
                |  "platform":"chess.com",
                |  "board":"????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"
                |}
                |""".stripMargin,
            java.nio.charset.StandardCharsets.UTF_8
          )
          for
            response <- Client.request(
              method = Method.POST,
              url = "http://localhost:8080/api/async/board",
              content = body
            )
            findResult <- response.body.to[FindResponse]
          yield findResult

        for {
          _            <- createUser
          _            <- `2022-07`
          _            <- `2022-08`
          _            <- `2022-09`
          _            <- `2022-10`
          _            <- `2022-11`
          actualResult <- findRequest
          gameIsFound  <- assertTrue(actualResult == expectedResult)
        } yield gameIsFound
      }
    ).provideShared(clientLayer ++ dynamodbLayer) @@ TestAspect.sequential
