// package chessfinder
// package flow

// import api.{ SearchResponse, TaskResponse, TaskStatusResponse }
// import client.*
// import client.ClientError.*
// import client.ClientExt.*
// import client.chess_com.ChessDotComClient
// import client.chess_com.*
// import persistence.{ GameRecord, PlatformType, UserRecord }
// import persistence.core.DefaultDynamoDBExecutor
// import search.GameDownloader
// import search.entity.{ ChessPlatform, UserName }
// import search.repo.UserRepo
// import sttp.model.Uri
// import sttp.model.Uri.UriContext
// import testkit.BroadIntegrationSuite
// import testkit.parser.JsonReader
// import testkit.wiremock.ClientBackdoor
// import util.RandomReadableString

// import com.typesafe.config.{ Config, ConfigFactory }
// import io.circe.*
// import zio.{ Duration as ZDuration, * }
// import zio.aws.core.config.AwsConfig
// import zio.aws.netty
// import zio.dynamodb.*
// import zio.http.{ Body, Client }
// import zio.http.model.Method
// import zio.http.service.{ ChannelFactory, EventLoopGroup }
// import zio.test.*

// import scala.concurrent.duration.*
// import scala.io.Source
// import scala.util.Success

// object DownloadGamesSpec extends BroadIntegrationSuite:

//   protected lazy val `chess.com` = ClientBackdoor("/chess_com")
//   protected lazy val clientLayer = Client.default.orDie

//   def spec =
//     suite("Chessfinder when the username and platform are provided")(
//       test("should check that user exists and download all the user's games") {
//         val userName = RandomReadableString()
//         val userStub = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}")
//           .returnsJson(
//             s"""|
//                 |{
//                 |  "player_id": 191338281,
//                 |  "@id": "https://api.chess.com/pub/player/${userName}",
//                 |  "url": "https://www.chess.com/member/${userName}",
//                 |  "username": "${userName}",
//                 |  "followers": 10,
//                 |  "country": "https://api.chess.com/pub/country/AM",
//                 |  "last_online": 1678264516,
//                 |  "joined": 1658920370,
//                 |  "status": "premium",
//                 |  "is_streamer": false,
//                 |  "verified": false,
//                 |  "league": "Champion"
//                 |}
//                 |""".stripMargin
//           )
//           .stub()

//         val archivesStub = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/archives")
//           .returnsJson(
//             s"""|
//                 |{
//                 | "archives": [
//                 |  "https://example.com/pub/player/${userName}/games/2022/07",
//                 |  "https://example.com/pub/player/${userName}/games/2022/08"
//                 |  ]
//                 |}
//                 |""".stripMargin
//           )
//           .stub()

//         val `2022-07` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/07")
//           .returnsJson(JsonReader.readResource("samples/2022-07_few_games.json"))
//           .stub()

//         val `2022-08` = `chess.com`
//           .expectsEndpoint("GET", s"/pub/player/${userName}/games/2022/08")
//           .returnsJson(JsonReader.readResource("samples/2022-08_few_games.json"))
//           .stub()

//         val createTask =
//           val body = Body.fromString(
//             s"""|{
//                 |  "user":"${userName}",
//                 |  "platform":"chess.com"
//                 |}
//                 |""".stripMargin,
//             java.nio.charset.StandardCharsets.UTF_8
//           )
//           for
//             res <- Client.request(
//               method = Method.POST,
//               url = "http://localhost:8080/api/async/game",
//               content = body
//             )
//             task <- res.body.to[TaskResponse]
//           yield task

//         def getTaskStatus(taskResponse: TaskResponse) =
//           for
//             response <- Client.request(url =
//               s"http://localhost:8080/api/async/task?taskId=${taskResponse.taskId.toString()}"
//             )
//             status <- response.body.to[TaskStatusResponse]
//           // _      <- ZIO.sleep(ZDuration.fromScala(1.seconds))
//           yield status

//         def makeExpectedTaskStatus(actualResult: TaskStatusResponse) =
//           TaskStatusResponse(
//             taskId = actualResult.taskId,
//             failed = 0,
//             succeed = 2,
//             done = 2,
//             pending = 0,
//             total = 2
//           )

//         val checkUser =
//           UserRecord.Table
//             .get[UserRecord](UserName(userName), PlatformType.fromPlatform(ChessPlatform.ChessDotCom))
//             .flatMap(ZIO.fromEither)

//         val checkGames =
//           for
//             user  <- checkUser
//             games <- GameRecord.Table.list[GameRecord](user.user_id)
//           yield games

//         val expectedAmountOfGames = 9

//         for
//           _                <- userStub
//           _                <- archivesStub
//           _                <- `2022-07`
//           _                <- `2022-08`
//           taskReponse      <- createTask
//           actualTaskStatus <- getTaskStatus(taskReponse).repeatUntil(_.pending == 0)
//           expectedTaskStatus = makeExpectedTaskStatus(actualTaskStatus)
//           equality          <- assertTrue(expectedTaskStatus == actualTaskStatus)
//           cachedGames       <- checkGames
//           gameAreCached     <- assertTrue(cachedGames.length == expectedAmountOfGames)
//           userStubCheck     <- `chess.com`.check(1, "GET", s"/pub/player/${userName}")
//           archivesStubCheck <- `chess.com`.check(1, "GET", s"/pub/player/${userName}/games/archives")
//           `2022-07_check`   <- `chess.com`.check(1, "GET", s"/pub/player/${userName}/games/2022/07")
//           `2022-08_check`   <- `chess.com`.check(1, "GET", s"/pub/player/${userName}/games/2022/08")
//         yield equality && gameAreCached && userStubCheck && userStubCheck && archivesStubCheck && `2022-07_check` && `2022-08_check`

//       }
//     ).provideShared(clientLayer ++ dynamodbLayer) @@ TestAspect.sequential @@ TestAspect.ignore
