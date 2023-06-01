// package chessfinder
// package flow

// import api.DownloadAndFindResponse
// import client.*
// import client.ClientError.*
// import client.ClientExt.*
// import client.chess_com.ChessDotComClient
// import client.chess_com.dto.*
// import search.entity.UserName
// import sttp.model.Uri
// import sttp.model.Uri.UriContext
// import testkit.BroadIntegrationSuite
// import testkit.parser.JsonReader
// import testkit.wiremock.ClientBackdoor
// import util.RandomReadableString

// import io.circe.*
// import zio.*
// import zio.http.{Body, Client}
// import zio.http.service.{ChannelFactory, EventLoopGroup}
// import zio.test.*

// import scala.io.Source
// import scala.util.Success

// object DownloadAndFindGameSpec extends BroadIntegrationSuite:

//   protected lazy val `chess.com` = ClientBackdoor("/chess_com")
//   protected lazy val clientLayer = Client.default.orDie

//   def spec =
//     suite("Chessfinder when the game is provided")(
//       test("and everything is OK, should find the game") {

//         val userName = RandomReadableString()

//         val archivesStub = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/archives")
//           .returnsJson(
//             s"""|
//                 |{
//                 | "archives": [
//                 |  "https://example.com/pub/player/${userName}/games/2022/07",
//                 |  "https://example.com/pub/player/${userName}/games/2022/08",
//                 |  "https://example.com/pub/player/${userName}/games/2022/09",
//                 |  "https://example.com/pub/player/${userName}/games/2022/10",
//                 |  "https://example.com/pub/player/${userName}/games/2022/11"
//                 |  ]
//                 |}
//                 |""".stripMargin
//           )
//           .stub()

//         val `2022-07` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/07")
//           .returnsJson(JsonReader.readResource("samples/2022-07.json"))
//           .stub()

//         val `2022-08` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/08")
//           .returnsJson(JsonReader.readResource("samples/2022-08.json"))
//           .stub()

//         val `2022-09` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/09")
//           .returnsJson(JsonReader.readResource("samples/2022-09.json"))
//           .stub()

//         val `2022-10` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/10")
//           .returnsJson(JsonReader.readResource("samples/2022-10.json"))
//           .stub()

//         val `2022-11` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/11")
//           .returnsJson(JsonReader.readResource("samples/2022-11.json"))
//           .stub()

//         val expectedResult = DownloadAndFindResponse(
//           resources = Seq(uri"https://www.chess.com/game/live/63025767719"),
//           message = "All games are successfully downloaded and analized."
//         )

//         val actualResult =
//           val body = Body.fromString(
//             s"""|{
//                 |  "user":"${userName}",
//                 |  "platform":"chess.com",
//                 |  "board":"????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"
//                 |}
//                 |""".stripMargin,
//             java.nio.charset.StandardCharsets.UTF_8
//           )
//           for
//             _            <- archivesStub
//             _            <- `2022-07`
//             _            <- `2022-08`
//             _            <- `2022-09`
//             _            <- `2022-10`
//             _            <- `2022-11`
//             res          <- Client.request(url = "http://localhost:8080/api/newborn/game", content = body)
//             actualResult <- res.body.to[DownloadAndFindResponse]
//           yield actualResult

//         val stubVerification =
//           for {
//             _ <- ZIO.unit
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/archives")
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/2022/07")
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/2022/08")
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/2022/09")
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/2022/10")
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/2022/11")
//           } yield ()

//         assertZIO(actualResult)(Assertion.equalTo(expectedResult)) &&
//         assertZIO(stubVerification)(Assertion.isUnit)
//       },
//       test(
//         "and everything is OK except one call for getting an archive, should find the game and notifiy that not all games were analized"
//       ) {

//         val userName = RandomReadableString()

//         val archivesStub = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/archives")
//           .returnsJson(
//             s"""|
//                 |{
//                 | "archives": [
//                 |  "https://example.com/pub/player/${userName}/games/2022/07",
//                 |  "https://example.com/pub/player/${userName}/games/2022/08",
//                 |  "https://example.com/pub/player/${userName}/games/2022/09",
//                 |  "https://example.com/pub/player/${userName}/games/2022/10",
//                 |  "https://example.com/pub/player/${userName}/games/2022/11"
//                 |  ]
//                 |}
//                 |""".stripMargin
//           )
//           .stub()

//         val `2022-07` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/07")
//           .returnsJson(JsonReader.readResource("samples/2022-07.json"))
//           .stub()

//         val `2022-08` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/08")
//           .returnsJson(
//             """|
//                |{
//                |  "code": 0,
//                |  "message": "Too many requests!"
//                |}
//                |""".stripMargin
//           )
//           .returnsStatusCode(429)
//           .stub()

//         val `2022-09` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/09")
//           .returnsJson(JsonReader.readResource("samples/2022-09.json"))
//           .stub()

//         val `2022-10` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/10")
//           .returnsJson(JsonReader.readResource("samples/2022-10.json"))
//           .stub()

//         val `2022-11` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/11")
//           .returnsJson(JsonReader.readResource("samples/2022-11.json"))
//           .stub()

//         val expectedResult = DownloadAndFindResponse(
//           resources = Seq(uri"https://www.chess.com/game/live/63025767719"),
//           message = "Not all games are downloaded and analized."
//         )

//         val actualResult =
//           val body = Body.fromString(
//             s"""|{
//                 |  "user":"${userName}",
//                 |  "platform":"chess.com",
//                 |  "board":"????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"
//                 |}
//                 |""".stripMargin,
//             java.nio.charset.StandardCharsets.UTF_8
//           )
//           for
//             _            <- archivesStub
//             _            <- `2022-07`
//             _            <- `2022-08`
//             _            <- `2022-09`
//             _            <- `2022-10`
//             _            <- `2022-11`
//             res          <- Client.request(url = "http://localhost:8080/api/newborn/game", content = body)
//             actualResult <- res.body.to[DownloadAndFindResponse]
//           yield actualResult

//         val stubVerification =
//           for {
//             _ <- ZIO.unit
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/archives")
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/2022/07")
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/2022/08")
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/2022/09")
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/2022/10")
//             _ <- `chess.com`.verify(1, "GET", s"/pub/player/${userName}/games/2022/11")
//           } yield ()

//         assertZIO(actualResult)(Assertion.equalTo(expectedResult)) &&
//         assertZIO(stubVerification)(Assertion.isUnit)
//       }
//     ).provideShared(clientLayer) @@ TestAspect.sequential
