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

object FindGameSpec extends ZIOSpecDefault with IntegrationSuite:

  protected lazy val `chess.com` = ClientBackdoor("/chess_com")
  protected lazy val clientEnv   = Client.default.orDie

  def spec =
    suite("Chessfinder when the game is provided")(
      test("should try to get all archives") {
        val profileStub = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137")
          .returnsJson(
            """|
               |{
               |  "player_id": 191338281,
               |  "@id": "https://pub/player/tigran-c-137",
               |  "url": "https://www.chess.com/member/tigran-c-137",
               |  "username": "tigran-c-137",
               |  "followers": 10,
               |  "country": "https://pub/country/AM",
               |  "last_online": 1678264516,
               |  "joined": 1658920370,
               |  "status": "premium",
               |  "is_streamer": false,
               |  "verified": false,
               |  "league": "Champion"
               |}
               |""".stripMargin
          )
          .stub()

        val archivesStub = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/archives")
          .returnsJson(
            """|
               |{
               | "archives": [
               |  "https://pub/player/tigran-c-137/games/2022/07",
               |  "https://pub/player/tigran-c-137/games/2022/08",
               |  "https://pub/player/tigran-c-137/games/2022/09",
               |  "https://pub/player/tigran-c-137/games/2022/10",
               |  "https://pub/player/tigran-c-137/games/2022/11"
               |  ]
               |}
               |""".stripMargin
          )
          .stub()

        val `2022-07` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/2022/07")
          .returnsJson(readJson("samples/2022-07.json"))
          .stub()

        val `2022-08` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/2022/08")
          .returnsJson(readJson("samples/2022-08.json"))
          .stub()
        
        val `2022-09` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/2022/09")
          .returnsJson(readJson("samples/2022-09.json"))
          .stub()
        
        val `2022-10` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/2022/10")
          .returnsJson(readJson("samples/2022-10.json"))
          .stub()
        
        val `2022-11` = `chess.com`
          .expectsEndpoint("GET", "/pub/player/tigran-c-137/games/2022/11")
          .returnsJson(readJson("samples/2022-11.json"))
          .stub()



        val expectedResult = FindResponse(
          resources = Seq.empty,
          message = "No meassage"
        )

        val actualResult =
          val body = Body.fromString(
            """|{
               |  "user":"tigran-c-137",
               |  "platform":"chess.com",
               |  "board":"a stupid thing"
               |}
               |""".stripMargin,
            java.nio.charset.StandardCharsets.UTF_8
          )
          for
            res          <- Client.request(url = "http://localhost:8080/api/newborn/game", content = body)
            actualResult <- res.body.to[FindResponse]
          yield actualResult

        val stubVerification =
          for {
            _ <- ZIO.unit
            // _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137")
            // _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/archives")
            // _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/2022/07")
            // _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/2022/08")
            // _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/2022/09")
            // _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/2022/10")
            // _ <- `chess.com`.verify(1, "GET", "/pub/player/tigran-c-137/games/2022/11")
          } yield ()
        
        assertZIO(actualResult.provide(clientEnv))(Assertion.equalTo(expectedResult)) &&
        assertZIO(stubVerification)(Assertion.isUnit)
      }
    )

  private def readJson(resource: String): String =
    val str = Source.fromResource(resource).mkString
    parser
      .parse(str)
      .map(_.spaces2)
      .toTry
      .get
