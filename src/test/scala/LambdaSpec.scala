import munit.FunSuite
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.ClientContext
import chessfinder.api.Lambda
import io.circe.parser

class LambdaSpec extends FunSuite {

  test("Lambda should find all mathces from the given games") {
    
    val inputStr = """
        |{
        |"requestId": "123",
        |"board": "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????",
        |"games": [
        |  {
        |    "pgn": "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.11.24\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"philimon93\"]\n[Result \"1-0\"]\n[CurrentPosition \"4R1r1/1p3kq1/p3Q3/3p1p2/BP6/P7/6PP/7K b - -\"]\n[Timezone \"UTC\"]\n[ECO \"B06\"]\n[ECOUrl \"https://www.chess.com/openings/Modern-Defense-Three-Pawns-Attack-with-2-f4\"]\n[UTCDate \"2022.11.24\"]\n[UTCTime \"10:44:47\"]\n[WhiteElo \"1533\"]\n[BlackElo \"1427\"]\n[TimeControl \"600\"]\n[Termination \"tigran-c-137 won by checkmate\"]\n[StartTime \"10:44:47\"]\n[EndDate \"2022.11.24\"]\n[EndTime \"11:04:16\"]\n[Link \"https://www.chess.com/game/live/63025767719\"]\n\n1. e4 {[%clk 0:09:57.7]} 1... g6 {[%clk 0:09:57.3]} 2. f4 {[%clk 0:09:52.6]} 2... e6 {[%clk 0:09:49.4]} 3. Nf3 {[%clk 0:09:50.5]} 3... Nc6 {[%clk 0:09:47.1]} 4. Be2 {[%clk 0:09:33.3]} 4... Nge7 {[%clk 0:09:43.3]} 5. O-O {[%clk 0:09:30.2]} 5... d5 {[%clk 0:09:41]} 6. exd5 {[%clk 0:08:58.4]} 6... Nxd5 {[%clk 0:09:39.5]} 7. d3 {[%clk 0:08:50.1]} 7... Bd6 {[%clk 0:09:30]} 8. f5 {[%clk 0:08:08.2]} 8... exf5 {[%clk 0:09:20.6]} 9. Bh6 {[%clk 0:08:07.8]} 9... Be6 {[%clk 0:09:01.4]} 10. c4 {[%clk 0:07:47.6]} 10... Nde7 {[%clk 0:08:39]} 11. d4 {[%clk 0:07:16.9]} 11... Bd7 {[%clk 0:08:10]} 12. d5 {[%clk 0:07:00.8]} 12... Bc5+ {[%clk 0:08:08.2]} 13. Kh1 {[%clk 0:06:59.2]} 13... Na5 {[%clk 0:07:52]} 14. a3 {[%clk 0:06:49.6]} 14... Bb6 {[%clk 0:07:07.9]} 15. b4 {[%clk 0:06:21.7]} 15... Nxc4 {[%clk 0:06:50.4]} 16. Bxc4 {[%clk 0:06:19.1]} 16... c6 {[%clk 0:06:49.4]} 17. d6 {[%clk 0:06:04.1]} 17... Nd5 {[%clk 0:06:06.5]} 18. Qe1+ {[%clk 0:05:01.1]} 18... Be6 {[%clk 0:06:03.8]} 19. Nc3 {[%clk 0:04:55]} 19... Qxd6 {[%clk 0:05:41.5]} 20. Nxd5 {[%clk 0:04:38.6]} 20... cxd5 {[%clk 0:05:39.5]} 21. Bb5+ {[%clk 0:04:21.9]} 21... Ke7 {[%clk 0:05:15.1]} 22. Bg7 {[%clk 0:03:31.6]} 22... Rhf8 {[%clk 0:04:20.9]} 23. Qh4+ {[%clk 0:03:15.5]} 23... f6 {[%clk 0:04:19.7]} 24. Bxf8+ {[%clk 0:03:14.9]} 24... Rxf8 {[%clk 0:04:17.5]} 25. Qxh7+ {[%clk 0:03:13]} 25... Rf7 {[%clk 0:04:16.6]} 26. Qg8 {[%clk 0:02:36.6]} 26... Rf8 {[%clk 0:03:43.5]} 27. Qxg6 {[%clk 0:02:35.7]} 27... Bc7 {[%clk 0:03:36.9]} 28. Rae1 {[%clk 0:02:09.5]} 28... a6 {[%clk 0:03:21.1]} 29. Ba4 {[%clk 0:01:58.3]} 29... Rg8 {[%clk 0:03:07.1]} 30. Qh7+ {[%clk 0:01:32.5]} 30... Kf8 {[%clk 0:02:50.8]} 31. Qh6+ {[%clk 0:00:58.7]} 31... Kf7 {[%clk 0:02:38.5]} 32. Qh5+ {[%clk 0:00:18.8]} 32... Ke7 {[%clk 0:01:45]} 33. Rxe6+ {[%clk 0:00:17.4]} 33... Qxe6 {[%clk 0:01:28.6]} 34. Re1 {[%clk 0:00:16.7]} 34... Be5 {[%clk 0:01:03.4]} 35. Nxe5 {[%clk 0:00:15.5]} 35... fxe5 {[%clk 0:00:58.8]} 36. Qh7+ {[%clk 0:00:14.4]} 36... Qf7 {[%clk 0:00:52.7]} 37. Rxe5+ {[%clk 0:00:13.1]} 37... Kf8 {[%clk 0:00:52.4]} 38. Qh6+ {[%clk 0:00:09.6]} 38... Qg7 {[%clk 0:00:45.8]} 39. Re8+ {[%clk 0:00:07.3]} 39... Kf7 {[%clk 0:00:45.4]} 40. Qe6# {[%clk 0:00:06.2]} 1-0\n",
        |    "id": "https://www.chess.com/game/live/52671679953"
        |  },
        |  {
        |    "pgn": "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.07.27\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"Garevia\"]\n[Result \"1-0\"]\n[CurrentPosition \"r6r/p3Bp1p/4p1k1/3P1RQ1/8/4P3/1P3P1P/4K1NR b K -\"]\n[Timezone \"UTC\"]\n[ECO \"D10\"]\n[ECOUrl \"https://www.chess.com/openings/Slav-Defense-3.Nc3-dxc4-4.e3\"]\n[UTCDate \"2022.07.27\"]\n[UTCTime \"11:18:00\"]\n[WhiteElo \"800\"]\n[BlackElo \"1200\"]\n[TimeControl \"300+5\"]\n[Termination \"tigran-c-137 won by checkmate\"]\n[StartTime \"11:18:00\"]\n[EndDate \"2022.07.27\"]\n[EndTime \"11:24:30\"]\n[Link \"https://www.chess.com/game/live/52659611873\"]\n\n1. d4 {[%clk 0:05:05]} 1... d5 {[%clk 0:05:05]} 2. c4 {[%clk 0:05:08.9]} 2... c6 {[%clk 0:05:08.5]} 3. Nc3 {[%clk 0:05:03.2]} 3... dxc4 {[%clk 0:05:11.7]} 4. e3 {[%clk 0:05:05.4]} 4... e6 {[%clk 0:05:12.4]} 5. Bxc4 {[%clk 0:05:06.8]} 5... Bb4 {[%clk 0:05:11.5]} 6. Bd2 {[%clk 0:05:05.5]} 6... b5 {[%clk 0:05:12.3]} 7. Be2 {[%clk 0:04:27]} 7... Bxc3 {[%clk 0:05:14.9]} 8. Bxc3 {[%clk 0:04:29.9]} 8... Na6 {[%clk 0:05:06.4]} 9. a4 {[%clk 0:04:32]} 9... Bd7 {[%clk 0:04:56.2]} 10. axb5 {[%clk 0:04:12]} 10... cxb5 {[%clk 0:04:59.7]} 11. Rxa6 {[%clk 0:04:15.5]} 11... Qc8 {[%clk 0:04:53.7]} 12. Ra5 {[%clk 0:04:13]} 12... Qc7 {[%clk 0:04:48.7]} 13. Bxb5 {[%clk 0:03:56.4]} 13... Bxb5 {[%clk 0:04:46]} 14. Rxb5 {[%clk 0:03:59.1]} 14... Qc6 {[%clk 0:04:36.5]} 15. Qa4 {[%clk 0:03:59.6]} 15... Qxg2 {[%clk 0:04:35.7]} 16. Rg5+ {[%clk 0:04:03.7]} 16... Ke7 {[%clk 0:04:27.9]} 17. Rxg2 {[%clk 0:04:02]} 17... Nf6 {[%clk 0:04:29.2]} 18. Rxg7 {[%clk 0:04:04.9]} 18... Ne4 {[%clk 0:04:31.1]} 19. Bb4+ {[%clk 0:03:48.8]} 19... Kf6 {[%clk 0:04:30]} 20. Rg4 {[%clk 0:03:42.2]} 20... Kf5 {[%clk 0:04:27]} 21. Rf4+ {[%clk 0:03:45.7]} 21... Kg5 {[%clk 0:04:21.4]} 22. Be7+ {[%clk 0:03:48.7]} 22... Kg6 {[%clk 0:04:22.8]} 23. d5 {[%clk 0:03:50.3]} 23... Nf6 {[%clk 0:04:19.5]} 24. Rxf6+ {[%clk 0:03:49.2]} 24... Kg5 {[%clk 0:04:20.8]} 25. Qf4+ {[%clk 0:03:51.5]} 25... Kh5 {[%clk 0:04:14.6]} 26. Rf5+ {[%clk 0:03:53]} 26... Kg6 {[%clk 0:04:17.5]} 27. Qg5# {[%clk 0:03:51.1]} 1-0\n",
        |    "id": "https://www.chess.com/game/live/52659611873"
        |  },
        |  {
        |    "pgn": "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.07.27\"]\n[Round \"-\"]\n[White \"Garevia\"]\n[Black \"tigran-c-137\"]\n[Result \"1/2-1/2\"]\n[CurrentPosition \"5k2/3K1P2/8/6n1/8/8/8/8 w - -\"]\n[Timezone \"UTC\"]\n[ECO \"D02\"]\n[ECOUrl \"https://www.chess.com/openings/Queens-Pawn-Opening-Krause-Variation-3.dxc5-e6\"]\n[UTCDate \"2022.07.27\"]\n[UTCTime \"11:36:38\"]\n[WhiteElo \"1200\"]\n[BlackElo \"800\"]\n[TimeControl \"300+5\"]\n[Termination \"Game drawn by agreement\"]\n[StartTime \"11:36:38\"]\n[EndDate \"2022.07.27\"]\n[EndTime \"11:51:38\"]\n[Link \"https://www.chess.com/game/live/52660795633\"]\n\n1. d4 {[%clk 0:05:05]} 1... d5 {[%clk 0:05:05]} 2. Nf3 {[%clk 0:05:08.9]} 2... c5 {[%clk 0:05:00.4]} 3. dxc5 {[%clk 0:05:08.3]} 3... e6 {[%clk 0:05:04.2]} 4. b4 {[%clk 0:05:05.6]} 4... a5 {[%clk 0:05:02.6]} 5. Bd2 {[%clk 0:05:08.4]} 5... Qf6 {[%clk 0:04:18.6]} 6. c3 {[%clk 0:05:05.3]} 6... axb4 {[%clk 0:04:17]} 7. Bg5 {[%clk 0:04:28.1]} 7... Qg6 {[%clk 0:03:52.1]} 8. cxb4 {[%clk 0:04:27.8]} 8... h6 {[%clk 0:03:54.9]} 9. Ne5 {[%clk 0:04:19.7]} 9... Qxg5 {[%clk 0:03:46]} 10. Nc3 {[%clk 0:03:58.3]} 10... Qxe5 {[%clk 0:03:05.2]} 11. Qc2 {[%clk 0:03:45.1]} 11... b6 {[%clk 0:02:59.9]} 12. cxb6 {[%clk 0:03:36.8]} 12... Bxb4 {[%clk 0:03:03.2]} 13. O-O-O {[%clk 0:03:29.1]} 13... Qxc3 {[%clk 0:02:54.1]} 14. Qxc3 {[%clk 0:03:30.6]} 14... Bxc3 {[%clk 0:02:57.9]} 15. e3 {[%clk 0:03:29.3]} 15... Rxa2 {[%clk 0:03:01.5]} 16. Bb5+ {[%clk 0:03:29.3]} 16... Bd7 {[%clk 0:03:02.1]} 17. Bxd7+ {[%clk 0:03:24.3]} 17... Nxd7 {[%clk 0:03:05.7]} 18. b7 {[%clk 0:03:28.4]} 18... Nb8 {[%clk 0:03:08]} 19. Kb1 {[%clk 0:03:30.4]} 19... Rb2+ {[%clk 0:03:09.9]} 20. Ka1 {[%clk 0:03:25.7]} 20... Rd2+ {[%clk 0:03:07.3]} 21. Kb1 {[%clk 0:03:27.1]} 21... Rb2+ {[%clk 0:03:10.2]} 22. Ka1 {[%clk 0:03:28.6]} 22... Nf6 {[%clk 0:03:13]} 23. Rc1 {[%clk 0:03:25.7]} 23... Rc2+ {[%clk 0:02:12.2]} 24. Kb1 {[%clk 0:03:25.4]} 24... Rxc1+ {[%clk 0:01:53.7]} 25. Rxc1 {[%clk 0:03:28.7]} 25... Ne4 {[%clk 0:01:57.5]} 26. f3 {[%clk 0:03:26.2]} 26... Nd2+ {[%clk 0:01:27.6]} 27. Ka2 {[%clk 0:03:11.7]} 27... Ke7 {[%clk 0:01:30.3]} 28. Rxc3 {[%clk 0:03:15.2]} 28... Kd6 {[%clk 0:01:26.9]} 29. Rc8 {[%clk 0:03:16.1]} 29... Rh7 {[%clk 0:01:09.8]} 30. Rxb8 {[%clk 0:03:13.5]} 30... Kc6 {[%clk 0:01:13.5]} 31. Ka3 {[%clk 0:03:07.9]} 31... Kc7 {[%clk 0:01:16.7]} 32. Rf8 {[%clk 0:03:01.8]} 32... Kxb7 {[%clk 0:01:20]} 33. Rxf7+ {[%clk 0:03:05.5]} 33... Kc6 {[%clk 0:01:20.9]} 34. Kb4 {[%clk 0:03:07.5]} 34... Rh8 {[%clk 0:01:22]} 35. e4 {[%clk 0:03:03.1]} 35... Rb8+ {[%clk 0:01:24.9]} 36. Kc3 {[%clk 0:03:06.4]} 36... Nb1+ {[%clk 0:01:25.3]} 37. Kd4 {[%clk 0:03:08]} 37... dxe4 {[%clk 0:01:20.2]} 38. fxe4 {[%clk 0:03:07.3]} 38... g5 {[%clk 0:01:23.1]} 39. Rf6 {[%clk 0:03:08.3]} 39... Kd6 {[%clk 0:01:11.6]} 40. e5+ {[%clk 0:03:09]} 40... Kd7 {[%clk 0:01:12]} 41. Rxh6 {[%clk 0:03:11.8]} 41... Rb4+ {[%clk 0:01:11.9]} 42. Kc5 {[%clk 0:03:13.2]} 42... Re4 {[%clk 0:01:11.5]} 43. Rh7+ {[%clk 0:03:09]} 43... Ke8 {[%clk 0:01:15.6]} 44. Kd6 {[%clk 0:03:11.5]} 44... Rh4 {[%clk 0:00:48.3]} 45. Re7+ {[%clk 0:03:07.5]} 45... Kf8 {[%clk 0:00:49.3]} 46. Kxe6 {[%clk 0:02:58.5]} 46... Rxh2 {[%clk 0:00:51.6]} 47. Rf7+ {[%clk 0:03:00.5]} 47... Kg8 {[%clk 0:00:55.5]} 48. Kf6 {[%clk 0:02:58.6]} 48... Rxg2 {[%clk 0:00:57]} 49. e6 {[%clk 0:03:01.9]} 49... Rf2+ {[%clk 0:01:00.2]} 50. Kxg5 {[%clk 0:03:04]} 50... Rxf7 {[%clk 0:01:02.9]} 51. exf7+ {[%clk 0:03:08]} 51... Kf8 {[%clk 0:01:07.2]} 52. Kf6 {[%clk 0:03:12]} 52... Nc3 {[%clk 0:01:09.5]} 53. Ke6 {[%clk 0:03:14.8]} 53... Ne4 {[%clk 0:01:11.9]} 54. Kd7 {[%clk 0:03:18.4]} 54... Ng5 {[%clk 0:01:15.9]} 1/2-1/2\n",
        |    "id": "https://www.chess.com/game/live/52660795633"
        |  },
        |  {
        |    "pgn": "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.07.27\"]\n[Round \"-\"]\n[White \"3Face_Tush\"]\n[Black \"tigran-c-137\"]\n[Result \"1-0\"]\n[CurrentPosition \"r2q1N1k/b1p2p2/p1n2NpQ/4pb2/p2P4/8/PP3PPP/R1B2RK1 b - -\"]\n[Timezone \"UTC\"]\n[ECO \"C78\"]\n[ECOUrl \"https://www.chess.com/openings/Ruy-Lopez-Opening-Morphy-Defense-Neo-Arkhangelsk-Variation-6.c3-O-O-7.d4\"]\n[UTCDate \"2022.07.27\"]\n[UTCTime \"11:53:35\"]\n[WhiteElo \"769\"]\n[BlackElo \"604\"]\n[TimeControl \"300+5\"]\n[Termination \"3Face_Tush won by checkmate\"]\n[StartTime \"11:53:35\"]\n[EndDate \"2022.07.27\"]\n[EndTime \"12:01:28\"]\n[Link \"https://www.chess.com/game/live/52661955709\"]\n\n1. e4 {[%clk 0:04:43.3]} 1... e5 {[%clk 0:05:03.4]} 2. Nf3 {[%clk 0:04:41.5]} 2... Nc6 {[%clk 0:05:08.3]} 3. Bb5 {[%clk 0:04:35.7]} 3... a6 {[%clk 0:05:04.4]} 4. Ba4 {[%clk 0:04:22.5]} 4... Nf6 {[%clk 0:05:06]} 5. O-O {[%clk 0:04:19.9]} 5... Bc5 {[%clk 0:05:05.4]} 6. c3 {[%clk 0:04:14.4]} 6... O-O {[%clk 0:05:04.1]} 7. d4 {[%clk 0:04:06.8]} 7... exd4 {[%clk 0:04:55.6]} 8. e5 {[%clk 0:04:05]} 8... Nd5 {[%clk 0:03:50.6]} 9. cxd4 {[%clk 0:04:02.3]} 9... Ba7 {[%clk 0:03:18.4]} 10. Nc3 {[%clk 0:03:59.2]} 10... b5 {[%clk 0:03:22.2]} 11. Nxd5 {[%clk 0:03:56.6]} 11... bxa4 {[%clk 0:03:24.9]} 12. Qd3 {[%clk 0:03:58.7]} 12... d6 {[%clk 0:03:10.1]} 13. Ng5 {[%clk 0:03:52]} 13... g6 {[%clk 0:02:25.9]} 14. Nf6+ {[%clk 0:03:48.4]} 14... Kh8 {[%clk 0:02:16]} 15. Ngxh7 {[%clk 0:03:51.6]} 15... dxe5 {[%clk 0:02:08.1]} 16. Qe4 {[%clk 0:03:48.1]} 16... Bf5 {[%clk 0:02:08]} 17. Qh4 {[%clk 0:03:40.6]} 17... Kg7 {[%clk 0:01:47.8]} 18. Qh6+ {[%clk 0:03:35.5]} 18... Kh8 {[%clk 0:01:41.2]} 19. Nxf8# {[%clk 0:03:40.4]} 1-0\n",
        |    "id": "https://www.chess.com/game/live/52661955709"
        |  },
        |  {
        |    "pgn": "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.07.27\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"3Face_Tush\"]\n[Result \"0-1\"]\n[CurrentPosition \"r5k1/3p2pp/1q2p3/p4r2/6Q1/5P1P/6K1/8 w - -\"]\n[Timezone \"UTC\"]\n[ECO \"A46\"]\n[ECOUrl \"https://www.chess.com/openings/Indian-Game-Spielmann-Indian-Variation-3.dxc5-e6\"]\n[UTCDate \"2022.07.27\"]\n[UTCTime \"12:01:48\"]\n[WhiteElo \"534\"]\n[BlackElo \"774\"]\n[TimeControl \"300+5\"]\n[Termination \"3Face_Tush won by resignation\"]\n[StartTime \"12:01:48\"]\n[EndDate \"2022.07.27\"]\n[EndTime \"12:12:44\"]\n[Link \"https://www.chess.com/game/live/52662068301\"]\n\n1. d4 {[%clk 0:05:03.1]} 1... Nf6 {[%clk 0:05:00.1]} 2. Nf3 {[%clk 0:05:05.8]} 2... c5 {[%clk 0:05:00.1]} 3. dxc5 {[%clk 0:04:54.5]} 3... e6 {[%clk 0:04:58.7]} 4. Nc3 {[%clk 0:04:55.2]} 4... Bxc5 {[%clk 0:04:57.5]} 5. h3 {[%clk 0:04:52.9]} 5... O-O {[%clk 0:04:54.8]} 6. Bg5 {[%clk 0:04:44.6]} 6... Qb6 {[%clk 0:04:48.5]} 7. e3 {[%clk 0:04:01.6]} 7... Qxb2 {[%clk 0:04:43.8]} 8. Bxf6 {[%clk 0:04:02.2]} 8... Bb4 {[%clk 0:04:38.7]} 9. Rb1 {[%clk 0:03:46]} 9... Bxc3+ {[%clk 0:04:34.3]} 10. Bxc3 {[%clk 0:03:41.5]} 10... Qxc3+ {[%clk 0:04:33.6]} 11. Nd2 {[%clk 0:03:43.6]} 11... b6 {[%clk 0:04:34.2]} 12. Rb3 {[%clk 0:03:43.8]} 12... Qc7 {[%clk 0:04:31.1]} 13. Bd3 {[%clk 0:03:46.3]} 13... Na6 {[%clk 0:04:27.9]} 14. O-O {[%clk 0:03:48.3]} 14... Bb7 {[%clk 0:04:23.8]} 15. Qh5 {[%clk 0:03:42.8]} 15... f5 {[%clk 0:04:19.6]} 16. g4 {[%clk 0:03:02.8]} 16... Nc5 {[%clk 0:04:17.2]} 17. Rc3 {[%clk 0:03:01.8]} 17... Qe5 {[%clk 0:04:14.9]} 18. Rxc5 {[%clk 0:02:33.3]} 18... Qxc5 {[%clk 0:04:08.6]} 19. Nc4 {[%clk 0:02:34.3]} 19... Qd5 {[%clk 0:04:06.5]} 20. f3 {[%clk 0:02:05.5]} 20... Ba6 {[%clk 0:04:03.9]} 21. Nb2 {[%clk 0:00:56.3]} 21... Bxd3 {[%clk 0:03:56.2]} 22. Nxd3 {[%clk 0:01:00.1]} 22... Qxa2 {[%clk 0:03:51.9]} 23. Nb4 {[%clk 0:00:45.5]} 23... Qc4 {[%clk 0:03:48.6]} 24. Rb1 {[%clk 0:00:35.2]} 24... a5 {[%clk 0:03:45.1]} 25. Nd3 {[%clk 0:00:17.2]} 25... Qxc2 {[%clk 0:03:48.6]} 26. Rxb6 {[%clk 0:00:14.9]} 26... Qxd3 {[%clk 0:03:46.7]} 27. gxf5 {[%clk 0:00:18.9]} 27... Qxe3+ {[%clk 0:03:50]} 28. Kg2 {[%clk 0:00:22.6]} 28... Rxf5 {[%clk 0:03:52.4]} 29. Qg4 {[%clk 0:00:20]} 29... Qxb6 {[%clk 0:03:55.8]} 0-1\n",
        |    "id": "https://www.chess.com/game/live/52662068301"
        |  },
        |  {
        |    "pgn": "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.11.24\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"philimon93\"]\n[Result \"1-0\"]\n[CurrentPosition \"4R1r1/1p3kq1/p3Q3/3p1p2/BP6/P7/6PP/7K b - -\"]\n[Timezone \"UTC\"]\n[ECO \"B06\"]\n[ECOUrl \"https://www.chess.com/openings/Modern-Defense-Three-Pawns-Attack-with-2-f4\"]\n[UTCDate \"2022.11.24\"]\n[UTCTime \"10:44:47\"]\n[WhiteElo \"1533\"]\n[BlackElo \"1427\"]\n[TimeControl \"600\"]\n[Termination \"tigran-c-137 won by checkmate\"]\n[StartTime \"10:44:47\"]\n[EndDate \"2022.11.24\"]\n[EndTime \"11:04:16\"]\n[Link \"https://www.chess.com/game/live/63025767719\"]\n\n1. e4 {[%clk 0:09:57.7]} 1... g6 {[%clk 0:09:57.3]} 2. f4 {[%clk 0:09:52.6]} 2... e6 {[%clk 0:09:49.4]} 3. Nf3 {[%clk 0:09:50.5]} 3... Nc6 {[%clk 0:09:47.1]} 4. Be2 {[%clk 0:09:33.3]} 4... Nge7 {[%clk 0:09:43.3]} 5. O-O {[%clk 0:09:30.2]} 5... d5 {[%clk 0:09:41]} 6. exd5 {[%clk 0:08:58.4]} 6... Nxd5 {[%clk 0:09:39.5]} 7. d3 {[%clk 0:08:50.1]} 7... Bd6 {[%clk 0:09:30]} 8. f5 {[%clk 0:08:08.2]} 8... exf5 {[%clk 0:09:20.6]} 9. Bh6 {[%clk 0:08:07.8]} 9... Be6 {[%clk 0:09:01.4]} 10. c4 {[%clk 0:07:47.6]} 10... Nde7 {[%clk 0:08:39]} 11. d4 {[%clk 0:07:16.9]} 11... Bd7 {[%clk 0:08:10]} 12. d5 {[%clk 0:07:00.8]} 12... Bc5+ {[%clk 0:08:08.2]} 13. Kh1 {[%clk 0:06:59.2]} 13... Na5 {[%clk 0:07:52]} 14. a3 {[%clk 0:06:49.6]} 14... Bb6 {[%clk 0:07:07.9]} 15. b4 {[%clk 0:06:21.7]} 15... Nxc4 {[%clk 0:06:50.4]} 16. Bxc4 {[%clk 0:06:19.1]} 16... c6 {[%clk 0:06:49.4]} 17. d6 {[%clk 0:06:04.1]} 17... Nd5 {[%clk 0:06:06.5]} 18. Qe1+ {[%clk 0:05:01.1]} 18... Be6 {[%clk 0:06:03.8]} 19. Nc3 {[%clk 0:04:55]} 19... Qxd6 {[%clk 0:05:41.5]} 20. Nxd5 {[%clk 0:04:38.6]} 20... cxd5 {[%clk 0:05:39.5]} 21. Bb5+ {[%clk 0:04:21.9]} 21... Ke7 {[%clk 0:05:15.1]} 22. Bg7 {[%clk 0:03:31.6]} 22... Rhf8 {[%clk 0:04:20.9]} 23. Qh4+ {[%clk 0:03:15.5]} 23... f6 {[%clk 0:04:19.7]} 24. Bxf8+ {[%clk 0:03:14.9]} 24... Rxf8 {[%clk 0:04:17.5]} 25. Qxh7+ {[%clk 0:03:13]} 25... Rf7 {[%clk 0:04:16.6]} 26. Qg8 {[%clk 0:02:36.6]} 26... Rf8 {[%clk 0:03:43.5]} 27. Qxg6 {[%clk 0:02:35.7]} 27... Bc7 {[%clk 0:03:36.9]} 28. Rae1 {[%clk 0:02:09.5]} 28... a6 {[%clk 0:03:21.1]} 29. Ba4 {[%clk 0:01:58.3]} 29... Rg8 {[%clk 0:03:07.1]} 30. Qh7+ {[%clk 0:01:32.5]} 30... Kf8 {[%clk 0:02:50.8]} 31. Qh6+ {[%clk 0:00:58.7]} 31... Kf7 {[%clk 0:02:38.5]} 32. Qh5+ {[%clk 0:00:18.8]} 32... Ke7 {[%clk 0:01:45]} 33. Rxe6+ {[%clk 0:00:17.4]} 33... Qxe6 {[%clk 0:01:28.6]} 34. Re1 {[%clk 0:00:16.7]} 34... Be5 {[%clk 0:01:03.4]} 35. Nxe5 {[%clk 0:00:15.5]} 35... fxe5 {[%clk 0:00:58.8]} 36. Qh7+ {[%clk 0:00:14.4]} 36... Qf7 {[%clk 0:00:52.7]} 37. Rxe5+ {[%clk 0:00:13.1]} 37... Kf8 {[%clk 0:00:52.4]} 38. Qh6+ {[%clk 0:00:09.6]} 38... Qg7 {[%clk 0:00:45.8]} 39. Re8+ {[%clk 0:00:07.3]} 39... Kf7 {[%clk 0:00:45.4]} 40. Qe6# {[%clk 0:00:06.2]} 1-0\n",
        |    "id": "https://www.chess.com/game/live/52671679954"
        |  }
        |]
        |}
    """.stripMargin
    val inputStream = new ByteArrayInputStream(inputStr.getBytes(StandardCharsets.UTF_8))
    val outputStream = new ByteArrayOutputStream()

    val context = new Context{

      override def getLogStreamName(): String = ???

      override def getMemoryLimitInMB(): Int = ???

      override def getRemainingTimeInMillis(): Int = ???

      override def getClientContext(): ClientContext = ???

      override def getInvokedFunctionArn(): String = ???

      override def getFunctionVersion(): String = ???

      override def getIdentity(): CognitoIdentity = ???

      override def getFunctionName(): String = ???

      override def getLogger(): LambdaLogger = ???

      override def getAwsRequestId(): String = ???

      override def getLogGroupName(): String = ???

    }

    Lambda.handleRequest(input = inputStream, output = outputStream, context = context)  
    
    val actualResposneStr = outputStream.toString(StandardCharsets.UTF_8)
    val actualResponseJson = parser.parse(actualResposneStr).toTry.get
    
    val expectedResponseStr = """
        |{
        |  "requestId" : "123",
        |  "machedGameIds" : [ "https://www.chess.com/game/live/52671679953", "https://www.chess.com/game/live/52671679954" ]
        |}
    """.stripMargin
    val expectedResponseJson = parser.parse(expectedResponseStr).toTry.get

    assertEquals(actualResponseJson, expectedResponseJson)
  }
  
}
