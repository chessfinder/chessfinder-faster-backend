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

object FindGameSpec extends ZIOSpecDefault with IntegrationSuite:

  protected lazy val `chess.com` = ClientBackdoor("/chess_com")
  protected lazy val clientEnv = Client.default.orDie

  def spec =
    suite("Chessfinder when the game is provided")(
      test("should try to get all archives") {

        println("stophere")

        val stub = `chess.com`
          .expectsEndpoint("GET", "/api.chess.com/pub/player/tigran-c-137")
          .returnsJson(
            """|
               |{
               |  "player_id": 191338281,
               |  "@id": "https://api.chess.com/pub/player/tigran-c-137",
               |  "url": "https://www.chess.com/member/tigran-c-137",
               |  "username": "tigran-c-137",
               |  "followers": 10,
               |  "country": "https://api.chess.com/pub/country/AM",
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
            res          <- Client.request(url = "http://localhost:8080", content = body)
            actualResult <- res.body.to[FindResponse]
          yield actualResult

        val stubVerification =
          `chess.com`.verify(1, "GET", "https://www.chess.com/member/tigran-c-137")

        assertZIO(actualResult.provide(clientEnv))(Assertion.equalTo(expectedResult)) &&
        assertZIO(stubVerification)(Assertion.isUnit)
      }
    )
