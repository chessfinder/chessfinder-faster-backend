package chessfinder
package core

import core.Arbitraries.given
import core.ProbabilisticPiece.{ CertainPiece, CertainlyOccupied, ProbablyOccupied }
import util.{ βUnsafeExt, DescriptionHelper }

import chess.ErrorStr.value
import chess.Replay
import chess.bitboard.Bitboard.*
import chess.bitboard.Board
import chess.format.Fen
import chess.format.pgn.{ PgnStr, Reader }
import chess.format.pgn.Reader.Result.{ Complete, Incomplete }
import munit.Clue.generate
import munit.{ FunSuite, ScalaCheckSuite }
import org.scalacheck.{ Arbitrary, Prop }

class PgnReaderTest extends FunSuite with βUnsafeExt with DescriptionHelper:

  test("""
  PgnReader for the game
  //// THE GAME ///
  should read succeessfully
  """.aline.ignore) {

    val pgn = PgnStr(
      "[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.07.27\"]\n[Round \"-\"]\n[White \"Garevia\"]\n[Black \"tigran-c-137\"]\n[Result \"1/2-1/2\"]\n[CurrentPosition \"5k2/3K1P2/8/6n1/8/8/8/8 w - -\"]\n[Timezone \"UTC\"]\n[ECO \"D02\"]\n[ECOUrl \"https://www.chess.com/openings/Queens-Pawn-Opening-Krause-Variation-3.dxc5-e6\"]\n[UTCDate \"2022.07.27\"]\n[UTCTime \"11:36:38\"]\n[WhiteElo \"1200\"]\n[BlackElo \"800\"]\n[TimeControl \"300+5\"]\n[Termination \"Game drawn by agreement\"]\n[StartTime \"11:36:38\"]\n[EndDate \"2022.07.27\"]\n[EndTime \"11:51:38\"]\n[Link \"https://www.chess.com/game/live/52660795633\"]\n\n1. d4 {[%clk 0:05:05]} 1... d5 {[%clk 0:05:05]} 2. Nf3 {[%clk 0:05:08.9]} 2... c5 {[%clk 0:05:00.4]} 3. dxc5 {[%clk 0:05:08.3]} 3... e6 {[%clk 0:05:04.2]} 4. b4 {[%clk 0:05:05.6]} 4... a5 {[%clk 0:05:02.6]} 5. Bd2 {[%clk 0:05:08.4]} 5... Qf6 {[%clk 0:04:18.6]} 6. c3 {[%clk 0:05:05.3]} 6... axb4 {[%clk 0:04:17]} 7. Bg5 {[%clk 0:04:28.1]} 7... Qg6 {[%clk 0:03:52.1]} 8. cxb4 {[%clk 0:04:27.8]} 8... h6 {[%clk 0:03:54.9]} 9. Ne5 {[%clk 0:04:19.7]} 9... Qxg5 {[%clk 0:03:46]} 10. Nc3 {[%clk 0:03:58.3]} 10... Qxe5 {[%clk 0:03:05.2]} 11. Qc2 {[%clk 0:03:45.1]} 11... b6 {[%clk 0:02:59.9]} 12. cxb6 {[%clk 0:03:36.8]} 12... Bxb4 {[%clk 0:03:03.2]} 13. O-O-O {[%clk 0:03:29.1]} 13... Qxc3 {[%clk 0:02:54.1]} 14. Qxc3 {[%clk 0:03:30.6]} 14... Bxc3 {[%clk 0:02:57.9]} 15. e3 {[%clk 0:03:29.3]} 15... Rxa2 {[%clk 0:03:01.5]} 16. Bb5+ {[%clk 0:03:29.3]} 16... Bd7 {[%clk 0:03:02.1]} 17. Bxd7+ {[%clk 0:03:24.3]} 17... Nxd7 {[%clk 0:03:05.7]} 18. b7 {[%clk 0:03:28.4]} 18... Nb8 {[%clk 0:03:08]} 19. Kb1 {[%clk 0:03:30.4]} 19... Rb2+ {[%clk 0:03:09.9]} 20. Ka1 {[%clk 0:03:25.7]} 20... Rd2+ {[%clk 0:03:07.3]} 21. Kb1 {[%clk 0:03:27.1]} 21... Rb2+ {[%clk 0:03:10.2]} 22. Ka1 {[%clk 0:03:28.6]} 22... Nf6 {[%clk 0:03:13]} 23. Rc1 {[%clk 0:03:25.7]} 23... Rc2+ {[%clk 0:02:12.2]} 24. Kb1 {[%clk 0:03:25.4]} 24... Rxc1+ {[%clk 0:01:53.7]} 25. Rxc1 {[%clk 0:03:28.7]} 25... Ne4 {[%clk 0:01:57.5]} 26. f3 {[%clk 0:03:26.2]} 26... Nd2+ {[%clk 0:01:27.6]} 27. Ka2 {[%clk 0:03:11.7]} 27... Ke7 {[%clk 0:01:30.3]} 28. Rxc3 {[%clk 0:03:15.2]} 28... Kd6 {[%clk 0:01:26.9]} 29. Rc8 {[%clk 0:03:16.1]} 29... Rh7 {[%clk 0:01:09.8]} 30. Rxb8 {[%clk 0:03:13.5]} 30... Kc6 {[%clk 0:01:13.5]} 31. Ka3 {[%clk 0:03:07.9]} 31... Kc7 {[%clk 0:01:16.7]} 32. Rf8 {[%clk 0:03:01.8]} 32... Kxb7 {[%clk 0:01:20]} 33. Rxf7+ {[%clk 0:03:05.5]} 33... Kc6 {[%clk 0:01:20.9]} 34. Kb4 {[%clk 0:03:07.5]} 34... Rh8 {[%clk 0:01:22]} 35. e4 {[%clk 0:03:03.1]} 35... Rb8+ {[%clk 0:01:24.9]} 36. Kc3 {[%clk 0:03:06.4]} 36... Nb1+ {[%clk 0:01:25.3]} 37. Kd4 {[%clk 0:03:08]} 37... dxe4 {[%clk 0:01:20.2]} 38. fxe4 {[%clk 0:03:07.3]} 38... g5 {[%clk 0:01:23.1]} 39. Rf6 {[%clk 0:03:08.3]} 39... Kd6 {[%clk 0:01:11.6]} 40. e5+ {[%clk 0:03:09]} 40... Kd7 {[%clk 0:01:12]} 41. Rxh6 {[%clk 0:03:11.8]} 41... Rb4+ {[%clk 0:01:11.9]} 42. Kc5 {[%clk 0:03:13.2]} 42... Re4 {[%clk 0:01:11.5]} 43. Rh7+ {[%clk 0:03:09]} 43... Ke8 {[%clk 0:01:15.6]} 44. Kd6 {[%clk 0:03:11.5]} 44... Rh4 {[%clk 0:00:48.3]} 45. Re7+ {[%clk 0:03:07.5]} 45... Kf8 {[%clk 0:00:49.3]} 46. Kxe6 {[%clk 0:02:58.5]} 46... Rxh2 {[%clk 0:00:51.6]} 47. Rf7+ {[%clk 0:03:00.5]} 47... Kg8 {[%clk 0:00:55.5]} 48. Kf6 {[%clk 0:02:58.6]} 48... Rxg2 {[%clk 0:00:57]} 49. e6 {[%clk 0:03:01.9]} 49... Rf2+ {[%clk 0:01:00.2]} 50. Kxg5 {[%clk 0:03:04]} 50... Rxf7 {[%clk 0:01:02.9]} 51. exf7+ {[%clk 0:03:08]} 51... Kf8 {[%clk 0:01:07.2]} 52. Kf6 {[%clk 0:03:12]} 52... Nc3 {[%clk 0:01:09.5]} 53. Ke6 {[%clk 0:03:14.8]} 53... Ne4 {[%clk 0:01:11.9]} 54. Kd7 {[%clk 0:03:18.4]} 54... Ng5 {[%clk 0:01:15.9]} 1/2-1/2\n"
    )

    val result = PgnReader.read(pgn)
    assertEquals(result.isValid, true)
  }
