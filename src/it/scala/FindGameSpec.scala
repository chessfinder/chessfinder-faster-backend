package chessfinder

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
import testkit.IntegrationSuite
import zio.http.Body
import client.ClientExt.*
import chessfinder.api.FindResponse
import api.FindResponse
import zio.http.Client
import io.circe.*
import io.circe.parser
import scala.io.Source
import testkit.parser.JsonReader

object FindGameSpec extends ZIOSpecDefault with IntegrationSuite:

  protected lazy val `chess.com` = ClientBackdoor("/chess_com")
  protected lazy val clientEnv   = Client.default.orDie

  def spec =
    suite("Chessfinder when the game is provided")(
      test("and everything is OK, should find the game") {
        val archivesStub = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/archives")
          .returnsJson(
            """|
               |{
               | "archives": [
               |  "https://example.com/pub/player/tigran-c-137/games/2022/07",
               |  "https://example.com/pub/player/tigran-c-137/games/2022/08",
               |  "https://example.com/pub/player/tigran-c-137/games/2022/09",
               |  "https://example.com/pub/player/tigran-c-137/games/2022/10",
               |  "https://example.com/pub/player/tigran-c-137/games/2022/11"
               |  ]
               |}
               |""".stripMargin
          )
          .stub()

        val `2022-07` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/2022/07")
          .returnsJson(JsonReader.readResource("samples/2022-07.json"))
          .stub()

        val `2022-08` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/2022/08")
          .returnsJson(JsonReader.readResource("samples/2022-08.json"))
          .stub()

        val `2022-09` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/2022/09")
          .returnsJson(JsonReader.readResource("samples/2022-09.json"))
          .stub()

        val `2022-10` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/2022/10")
          .returnsJson(JsonReader.readResource("samples/2022-10.json"))
          .stub()

        val `2022-11` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/2022/11")
          .returnsJson(JsonReader.readResource("samples/2022-11.json"))
          .stub()

        val expectedResult = FindResponse(
          resources = Seq(uri"https://www.chess.com/game/live/63025767719"),
          message = "All games are successfully downloaded and analized."
        )

        val actualResult =
          val body = Body.fromString(
            """|{
               |  "user":"tigran-c-137",
               |  "platform":"chess.com",
               |  "board":"????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"
               |}
               |""".stripMargin,
            java.nio.charset.StandardCharsets.UTF_8
          )
          for
            _            <- archivesStub
            _            <- `2022-07`
            _            <- `2022-08`
            _            <- `2022-09`
            _            <- `2022-10`
            _            <- `2022-11`
            res          <- Client.request(url = "http://localhost:8080/api/newborn/game", content = body)
            actualResult <- res.body.to[FindResponse]
          yield actualResult

        val stubVerification =
          for {
            _ <- ZIO.unit
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/archives")
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/2022/07")
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/2022/08")
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/2022/09")
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/2022/10")
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/2022/11")
          } yield ()

        assertZIO(actualResult.provide(clientEnv))(Assertion.equalTo(expectedResult)) &&
        assertZIO(stubVerification)(Assertion.isUnit)
      },
      test(
        "and everything is OK except one call for getting an archive, should find the game and notifiy that not all games were analized"
      ) {
        val archivesStub = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-138/games/archives")
          .returnsJson(
            """|
               |{
               | "archives": [
               |  "https://example.com/pub/player/tigran-c-138/games/2022/07",
               |  "https://example.com/pub/player/tigran-c-138/games/2022/08",
               |  "https://example.com/pub/player/tigran-c-138/games/2022/09",
               |  "https://example.com/pub/player/tigran-c-138/games/2022/10",
               |  "https://example.com/pub/player/tigran-c-138/games/2022/11"
               |  ]
               |}
               |""".stripMargin
          )
          .stub()

        val `2022-07` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-138/games/2022/07")
          .returnsJson(JsonReader.readResource("samples/2022-07.json"))
          .stub()

        val `2022-08` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-138/games/2022/08")
          .returnsJson(
            """|
               |{
               |  "code": 0,
               |  "message": "Too many requests!"
               |}
               |""".stripMargin
          )
          .returnsStatusCode(429)
          .stub()

        val `2022-09` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-138/games/2022/09")
          .returnsJson(JsonReader.readResource("samples/2022-09.json"))
          .stub()

        val `2022-10` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-138/games/2022/10")
          .returnsJson(JsonReader.readResource("samples/2022-10.json"))
          .stub()

        val `2022-11` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-138/games/2022/11")
          .returnsJson(JsonReader.readResource("samples/2022-11.json"))
          .stub()

        val expectedResult = FindResponse(
          resources = Seq(uri"https://www.chess.com/game/live/63025767719"),
          message = "Not all games are downloaded and analized."
        )

        val actualResult =
          val body = Body.fromString(
            """|{
               |  "user":"tigran-c-138",
               |  "platform":"chess.com",
               |  "board":"????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"
               |}
               |""".stripMargin,
            java.nio.charset.StandardCharsets.UTF_8
          )
          for
            _            <- archivesStub
            _            <- `2022-07`
            _            <- `2022-08`
            _            <- `2022-09`
            _            <- `2022-10`
            _            <- `2022-11`
            res          <- Client.request(url = "http://localhost:8080/api/newborn/game", content = body)
            actualResult <- res.body.to[FindResponse]
          yield actualResult

        val stubVerification =
          for {
            _ <- ZIO.unit
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-138/games/archives")
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-138/games/2022/07")
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-138/games/2022/08")
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-138/games/2022/09")
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-138/games/2022/10")
            _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-138/games/2022/11")
          } yield ()

        assertZIO(actualResult.provide(clientEnv))(Assertion.equalTo(expectedResult)) &&
        assertZIO(stubVerification)(Assertion.isUnit)
      }
    )
