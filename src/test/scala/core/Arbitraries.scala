package chessfinder
package core

import chess.bitboard.{ Bitboard, Board }
import org.scalacheck.{ Arbitrary, Gen }

object Arbitraries:

  given (using board: Board): Arbitrary[RealisitcGuess] = Arbitrary(RealisitcGuess.gen(board))
  given (using board: Board): Arbitrary[WrongGuess]     = Arbitrary(WrongGuess.gen(board))
