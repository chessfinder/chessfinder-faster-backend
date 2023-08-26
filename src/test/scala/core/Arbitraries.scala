package chessfinder
package core

import chess.bitboard.Board
import org.scalacheck.Arbitrary

object Arbitraries:

  given (using board: Board): Arbitrary[RealisticGuess] = Arbitrary(RealisticGuess.gen(board))
  given (using board: Board): Arbitrary[WrongGuess]     = Arbitrary(WrongGuess.gen(board))
