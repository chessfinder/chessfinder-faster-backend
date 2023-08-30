// package chessfinder
// package flow

// import api.SearchResponse
// import client.*
// import client.ClientError.*
// import client.ClientExt.*
// import client.chess_com.ChessDotComClient
// import client.chess_com.*
// import persistence.{GameRecord, UserRecord}
// import persistence.core.DefaultDynamoDBExecutor
// import search.*
// import sttp.model.Uri
// import sttp.model.Uri.UriContext
// import testkit.BroadIntegrationSuite
// import testkit.parser.JsonReader
// import testkit.wiremock.ClientBackdoor
// import util.RandomReadableString

// import chess.format.pgn.PgnStr
// import com.typesafe.config.{Config, ConfigFactory}
// import io.circe.{parser, *}
// import zio.*
// import zio.aws.core.config.AwsConfig
// import zio.aws.netty
// import zio.dynamodb.*
// import zio.http.{Body, Client}
// import zio.http.model.Method
// import zio.http.service.{ChannelFactory, EventLoopGroup}
// import zio.test.*

// import scala.io.Source
// import scala.util.Success

// object FindGameSpec extends BroadIntegrationSuite:

//   protected lazy val `chess.com` = ClientBackdoor("/chess_com")
//   protected lazy val clientLayer = Client.default.orDie

//   def spec =
//     suite("Chessfinder when the game is provided")(
//       test("and everything is OK, should find the game") {
//         val userName = RandomReadableString()
//         val userId   = UserId(RandomReadableString())
//         val user     = UserIdentified(ChessPlatform.ChessDotCom, UserName(userName), userId)

//         val createUser = UserRecord.Table.put(UserRecord.fromUserIdentified(user))

//         def readGames(resource: String) = ZIO.attempt {
//           val gamesAsJson = JsonReader.parseResource(resource)
//           val games       = Decoder[Games].decodeJson(gamesAsJson).toTry.get
//           games.games
//             .map(game => HistoricalGame(game.url, PgnStr(game.pgn)))
//             .map(game => GameRecord.fromGame(userId, game))
//         }

//         def fillDate(games: Seq[GameRecord]) =
//           GameRecord.Table.putMany(games*)

//         val `2022-07` = readGames(s"samples/2022-07.json").flatMap(fillDate)

//         val `2022-08` = readGames(s"samples/2022-08.json").flatMap(fillDate)

//         val `2022-09` = readGames(s"samples/2022-09.json").flatMap(fillDate)

//         val `2022-10` = readGames(s"samples/2022-10.json").flatMap(fillDate)

//         val `2022-11` = readGames(s"samples/2022-11.json").flatMap(fillDate)

//         val expectedResult = SearchResponse(
//           resources = Seq(uri"https://www.chess.com/game/live/63025767719"),
//           message = "All games are successfully analized."
//         )

//         val findRequest =
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
//             response <- Client.request(
//               method = Method.POST,
//               url = "http://localhost:8080/api/async/board",
//               content = body
//             )
//             findResult <- response.body.to[SearchResponse]
//           yield findResult

//         for {
//           _            <- createUser
//           _            <- `2022-07`
//           _            <- `2022-08`
//           _            <- `2022-09`
//           _            <- `2022-10`
//           _            <- `2022-11`
//           actualResult <- findRequest
//           gameIsFound  <- assertTrue(actualResult == expectedResult)
//         } yield gameIsFound
//       }
//     ).provideShared(clientLayer ++ dynamodbLayer) @@ TestAspect.sequential
