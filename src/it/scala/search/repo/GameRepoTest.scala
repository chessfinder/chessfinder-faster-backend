package chessfinder
package search.repo

import client.*
import client.ClientError.*
import client.chess_com.ChessDotComClient
import client.chess_com.dto.*
import persistence.core.DefaultDynamoDBExecutor
import persistence.{ GameRecord, PlatformType, UserRecord }
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

object GameRepoTest extends NarrowIntegrationSuite:

  val repo = ZIO.service[GameRepo]

  def spec =
    suite("GameRepo")(
      suite("list")(
        test("should get all games from the database") {

          val userId = UserId(RandomReadableString())

          val game1 = GameRecord(
            userId,
            GameId("https://www.chess.com/game/live/52659611873"),
            UriParser("https://www.chess.com/game/live/52659611873").get,
            PgnStr(
              """[Event "Live Chess"]\n[Site "Chess.com"]\n[Date "2022.07.27"]\n[Round "-"]\n[White "tigran-c-137"]\n[Black "Garevia"]\n[Result "1-0"]\n[CurrentPosition "r6r/p3Bp1p/4p1k1/3P1RQ1/8/4P3/1P3P1P/4K1NR b K -"]\n[Timezone "UTC"]\n[ECO "D10"]\n[ECOUrl "https://www.chess.com/openings/Slav-Defense-3.Nc3-dxc4-4.e3"]\n[UTCDate "2022.07.27"]\n[UTCTime "11:18:00"]\n[WhiteElo "800"]\n[BlackElo "1200"]\n[TimeControl "300+5"]\n[Termination "tigran-c-137 won by checkmate"]\n[StartTime "11:18:00"]\n[EndDate "2022.07.27"]\n[EndTime "11:24:30"]\n[Link "https://www.chess.com/game/live/52659611873"]\n\n1. d4 {[%clk 0:05:05]} 1... d5 {[%clk 0:05:05]} 2. c4 {[%clk 0:05:08.9]} 2... c6 {[%clk 0:05:08.5]} 3. Nc3 {[%clk 0:05:03.2]} 3... dxc4 {[%clk 0:05:11.7]} 4. e3 {[%clk 0:05:05.4]} 4... e6 {[%clk 0:05:12.4]} 5. Bxc4 {[%clk 0:05:06.8]} 5... Bb4 {[%clk 0:05:11.5]} 6. Bd2 {[%clk 0:05:05.5]} 6... b5 {[%clk 0:05:12.3]} 7. Be2 {[%clk 0:04:27]} 7... Bxc3 {[%clk 0:05:14.9]} 8. Bxc3 {[%clk 0:04:29.9]} 8... Na6 {[%clk 0:05:06.4]} 9. a4 {[%clk 0:04:32]} 9... Bd7 {[%clk 0:04:56.2]} 10. axb5 {[%clk 0:04:12]} 10... cxb5 {[%clk 0:04:59.7]} 11. Rxa6 {[%clk 0:04:15.5]} 11... Qc8 {[%clk 0:04:53.7]} 12. Ra5 {[%clk 0:04:13]} 12... Qc7 {[%clk 0:04:48.7]} 13. Bxb5 {[%clk 0:03:56.4]} 13... Bxb5 {[%clk 0:04:46]} 14. Rxb5 {[%clk 0:03:59.1]} 14... Qc6 {[%clk 0:04:36.5]} 15. Qa4 {[%clk 0:03:59.6]} 15... Qxg2 {[%clk 0:04:35.7]} 16. Rg5+ {[%clk 0:04:03.7]} 16... Ke7 {[%clk 0:04:27.9]} 17. Rxg2 {[%clk 0:04:02]} 17... Nf6 {[%clk 0:04:29.2]} 18. Rxg7 {[%clk 0:04:04.9]} 18... Ne4 {[%clk 0:04:31.1]} 19. Bb4+ {[%clk 0:03:48.8]} 19... Kf6 {[%clk 0:04:30]} 20. Rg4 {[%clk 0:03:42.2]} 20... Kf5 {[%clk 0:04:27]} 21. Rf4+ {[%clk 0:03:45.7]} 21... Kg5 {[%clk 0:04:21.4]} 22. Be7+ {[%clk 0:03:48.7]} 22... Kg6 {[%clk 0:04:22.8]} 23. d5 {[%clk 0:03:50.3]} 23... Nf6 {[%clk 0:04:19.5]} 24. Rxf6+ {[%clk 0:03:49.2]} 24... Kg5 {[%clk 0:04:20.8]} 25. Qf4+ {[%clk 0:03:51.5]} 25... Kh5 {[%clk 0:04:14.6]} 26. Rf5+ {[%clk 0:03:53]} 26... Kg6 {[%clk 0:04:17.5]} 27. Qg5# {[%clk 0:03:51.1]} 1-0\n"""
            )
          )

          val game2 = GameRecord(
            userId,
            GameId("https://www.chess.com/game/live/52660795633"),
            UriParser("https://www.chess.com/game/live/52660795633").get,
            PgnStr(
              """[Event "Live Chess"]\n[Site "Chess.com"]\n[Date "2022.07.27"]\n[Round "-"]\n[White "Garevia"]\n[Black "tigran-c-137"]\n[Result "1/2-1/2"]\n[CurrentPosition "5k2/3K1P2/8/6n1/8/8/8/8 w - -"]\n[Timezone "UTC"]\n[ECO "D02"]\n[ECOUrl "https://www.chess.com/openings/Queens-Pawn-Opening-Krause-Variation-3.dxc5-e6"]\n[UTCDate "2022.07.27"]\n[UTCTime "11:36:38"]\n[WhiteElo "1200"]\n[BlackElo "800"]\n[TimeControl "300+5"]\n[Termination "Game drawn by agreement"]\n[StartTime "11:36:38"]\n[EndDate "2022.07.27"]\n[EndTime "11:51:38"]\n[Link "https://www.chess.com/game/live/52660795633"]\n\n1. d4 {[%clk 0:05:05]} 1... d5 {[%clk 0:05:05]} 2. Nf3 {[%clk 0:05:08.9]} 2... c5 {[%clk 0:05:00.4]} 3. dxc5 {[%clk 0:05:08.3]} 3... e6 {[%clk 0:05:04.2]} 4. b4 {[%clk 0:05:05.6]} 4... a5 {[%clk 0:05:02.6]} 5. Bd2 {[%clk 0:05:08.4]} 5... Qf6 {[%clk 0:04:18.6]} 6. c3 {[%clk 0:05:05.3]} 6... axb4 {[%clk 0:04:17]} 7. Bg5 {[%clk 0:04:28.1]} 7... Qg6 {[%clk 0:03:52.1]} 8. cxb4 {[%clk 0:04:27.8]} 8... h6 {[%clk 0:03:54.9]} 9. Ne5 {[%clk 0:04:19.7]} 9... Qxg5 {[%clk 0:03:46]} 10. Nc3 {[%clk 0:03:58.3]} 10... Qxe5 {[%clk 0:03:05.2]} 11. Qc2 {[%clk 0:03:45.1]} 11... b6 {[%clk 0:02:59.9]} 12. cxb6 {[%clk 0:03:36.8]} 12... Bxb4 {[%clk 0:03:03.2]} 13. O-O-O {[%clk 0:03:29.1]} 13... Qxc3 {[%clk 0:02:54.1]} 14. Qxc3 {[%clk 0:03:30.6]} 14... Bxc3 {[%clk 0:02:57.9]} 15. e3 {[%clk 0:03:29.3]} 15... Rxa2 {[%clk 0:03:01.5]} 16. Bb5+ {[%clk 0:03:29.3]} 16... Bd7 {[%clk 0:03:02.1]} 17. Bxd7+ {[%clk 0:03:24.3]} 17... Nxd7 {[%clk 0:03:05.7]} 18. b7 {[%clk 0:03:28.4]} 18... Nb8 {[%clk 0:03:08]} 19. Kb1 {[%clk 0:03:30.4]} 19... Rb2+ {[%clk 0:03:09.9]} 20. Ka1 {[%clk 0:03:25.7]} 20... Rd2+ {[%clk 0:03:07.3]} 21. Kb1 {[%clk 0:03:27.1]} 21... Rb2+ {[%clk 0:03:10.2]} 22. Ka1 {[%clk 0:03:28.6]} 22... Nf6 {[%clk 0:03:13]} 23. Rc1 {[%clk 0:03:25.7]} 23... Rc2+ {[%clk 0:02:12.2]} 24. Kb1 {[%clk 0:03:25.4]} 24... Rxc1+ {[%clk 0:01:53.7]} 25. Rxc1 {[%clk 0:03:28.7]} 25... Ne4 {[%clk 0:01:57.5]} 26. f3 {[%clk 0:03:26.2]} 26... Nd2+ {[%clk 0:01:27.6]} 27. Ka2 {[%clk 0:03:11.7]} 27... Ke7 {[%clk 0:01:30.3]} 28. Rxc3 {[%clk 0:03:15.2]} 28... Kd6 {[%clk 0:01:26.9]} 29. Rc8 {[%clk 0:03:16.1]} 29... Rh7 {[%clk 0:01:09.8]} 30. Rxb8 {[%clk 0:03:13.5]} 30... Kc6 {[%clk 0:01:13.5]} 31. Ka3 {[%clk 0:03:07.9]} 31... Kc7 {[%clk 0:01:16.7]} 32. Rf8 {[%clk 0:03:01.8]} 32... Kxb7 {[%clk 0:01:20]} 33. Rxf7+ {[%clk 0:03:05.5]} 33... Kc6 {[%clk 0:01:20.9]} 34. Kb4 {[%clk 0:03:07.5]} 34... Rh8 {[%clk 0:01:22]} 35. e4 {[%clk 0:03:03.1]} 35... Rb8+ {[%clk 0:01:24.9]} 36. Kc3 {[%clk 0:03:06.4]} 36... Nb1+ {[%clk 0:01:25.3]} 37. Kd4 {[%clk 0:03:08]} 37... dxe4 {[%clk 0:01:20.2]} 38. fxe4 {[%clk 0:03:07.3]} 38... g5 {[%clk 0:01:23.1]} 39. Rf6 {[%clk 0:03:08.3]} 39... Kd6 {[%clk 0:01:11.6]} 40. e5+ {[%clk 0:03:09]} 40... Kd7 {[%clk 0:01:12]} 41. Rxh6 {[%clk 0:03:11.8]} 41... Rb4+ {[%clk 0:01:11.9]} 42. Kc5 {[%clk 0:03:13.2]} 42... Re4 {[%clk 0:01:11.5]} 43. Rh7+ {[%clk 0:03:09]} 43... Ke8 {[%clk 0:01:15.6]} 44. Kd6 {[%clk 0:03:11.5]} 44... Rh4 {[%clk 0:00:48.3]} 45. Re7+ {[%clk 0:03:07.5]} 45... Kf8 {[%clk 0:00:49.3]} 46. Kxe6 {[%clk 0:02:58.5]} 46... Rxh2 {[%clk 0:00:51.6]} 47. Rf7+ {[%clk 0:03:00.5]} 47... Kg8 {[%clk 0:00:55.5]} 48. Kf6 {[%clk 0:02:58.6]} 48... Rxg2 {[%clk 0:00:57]} 49. e6 {[%clk 0:03:01.9]} 49... Rf2+ {[%clk 0:01:00.2]} 50. Kxg5 {[%clk 0:03:04]} 50... Rxf7 {[%clk 0:01:02.9]} 51. exf7+ {[%clk 0:03:08]} 51... Kf8 {[%clk 0:01:07.2]} 52. Kf6 {[%clk 0:03:12]} 52... Nc3 {[%clk 0:01:09.5]} 53. Ke6 {[%clk 0:03:14.8]} 53... Ne4 {[%clk 0:01:11.9]} 54. Kd7 {[%clk 0:03:18.4]} 54... Ng5 {[%clk 0:01:15.9]} 1/2-1/2\n"""
            )
          )

          val game3 = GameRecord(
            userId,
            GameId("https://www.chess.com/game/live/52661955709"),
            UriParser("https://www.chess.com/game/live/52661955709").get,
            PgnStr(
              """[Event "Live Chess"]\n[Site "Chess.com"]\n[Date "2022.07.27"]\n[Round "-"]\n[White "3Face_Tush"]\n[Black "tigran-c-137"]\n[Result "1-0"]\n[CurrentPosition "r2q1N1k/b1p2p2/p1n2NpQ/4pb2/p2P4/8/PP3PPP/R1B2RK1 b - -"]\n[Timezone "UTC"]\n[ECO "C78"]\n[ECOUrl "https://www.chess.com/openings/Ruy-Lopez-Opening-Morphy-Defense-Neo-Arkhangelsk-Variation-6.c3-O-O-7.d4"]\n[UTCDate "2022.07.27"]\n[UTCTime "11:53:35"]\n[WhiteElo "769"]\n[BlackElo "604"]\n[TimeControl "300+5"]\n[Termination "3Face_Tush won by checkmate"]\n[StartTime "11:53:35"]\n[EndDate "2022.07.27"]\n[EndTime "12:01:28"]\n[Link "https://www.chess.com/game/live/52661955709"]\n\n1. e4 {[%clk 0:04:43.3]} 1... e5 {[%clk 0:05:03.4]} 2. Nf3 {[%clk 0:04:41.5]} 2... Nc6 {[%clk 0:05:08.3]} 3. Bb5 {[%clk 0:04:35.7]} 3... a6 {[%clk 0:05:04.4]} 4. Ba4 {[%clk 0:04:22.5]} 4... Nf6 {[%clk 0:05:06]} 5. O-O {[%clk 0:04:19.9]} 5... Bc5 {[%clk 0:05:05.4]} 6. c3 {[%clk 0:04:14.4]} 6... O-O {[%clk 0:05:04.1]} 7. d4 {[%clk 0:04:06.8]} 7... exd4 {[%clk 0:04:55.6]} 8. e5 {[%clk 0:04:05]} 8... Nd5 {[%clk 0:03:50.6]} 9. cxd4 {[%clk 0:04:02.3]} 9... Ba7 {[%clk 0:03:18.4]} 10. Nc3 {[%clk 0:03:59.2]} 10... b5 {[%clk 0:03:22.2]} 11. Nxd5 {[%clk 0:03:56.6]} 11... bxa4 {[%clk 0:03:24.9]} 12. Qd3 {[%clk 0:03:58.7]} 12... d6 {[%clk 0:03:10.1]} 13. Ng5 {[%clk 0:03:52]} 13... g6 {[%clk 0:02:25.9]} 14. Nf6+ {[%clk 0:03:48.4]} 14... Kh8 {[%clk 0:02:16]} 15. Ngxh7 {[%clk 0:03:51.6]} 15... dxe5 {[%clk 0:02:08.1]} 16. Qe4 {[%clk 0:03:48.1]} 16... Bf5 {[%clk 0:02:08]} 17. Qh4 {[%clk 0:03:40.6]} 17... Kg7 {[%clk 0:01:47.8]} 18. Qh6+ {[%clk 0:03:35.5]} 18... Kh8 {[%clk 0:01:41.2]} 19. Nxf8# {[%clk 0:03:40.4]} 1-0\n"""
            )
          )

          val expectedResult = Set(game1.toGame, game2.toGame, game3.toGame)

          for
            gameRepo     <- repo
            _            <- GameRecord.Table.putMany(game1, game2, game3)
            actualResult <- gameRepo.list(userId)
            result1      <- assertTrue(actualResult.toSet == expectedResult)
          yield result1
        }
      ),
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
    ).provideLayer(dynamodbLayer >+> GameRepo.Impl.layer) @@ TestAspect.sequential
