package chessfinder
package core

import org.scalacheck.{ Arbitrary, Gen }
import chess.bitboard.{ Bitboard, Board }

object Arbitraries:

  given (using board: Board): Arbitrary[RealisitcGuess] = Arbitrary(RealisitcGuess.gen(board))
  given (using board: Board): Arbitrary[WrongGuess]     = Arbitrary(WrongGuess.gen(board))
