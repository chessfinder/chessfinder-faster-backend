package chessfinder
package core

import chess.bitboard.{ Bitboard, Board }
import org.scalacheck.Gen

case class RealisticGuess(probabilisticBoard: ProbabilisticBoard)
object RealisticGuess:
  def gen(board: Board): Gen[RealisticGuess] = {
    for {
      pawns   <- Gen.long.map(n => board.pawns & n)
      knights <- Gen.long.map(n => board.knights & n)
      bishops <- Gen.long.map(n => board.bishops & n)
      rooks   <- Gen.long.map(n => board.rooks & n)
      queens  <- Gen.long.map(n => board.queens & n)
      kings   <- Gen.long.map(n => board.kings & n)
      occupied = pawns | knights | bishops | rooks | queens | kings
      white    = board.white & occupied
      black    = board.black & occupied
      certainBoard = Board(
        pawns = pawns,
        knights = knights,
        bishops = bishops,
        rooks = rooks,
        queens = queens,
        kings = kings,
        white = white,
        black = black,
        occupied = occupied
      )
      certainlyOccupiedByUnknown <- Gen.long.map(n => (~occupied) & board.occupied & n)
      totalOccupied = occupied | certainlyOccupiedByUnknown
      certainlyFree <- Gen.long.map(n => (~board.occupied) & n)
      maybeOccupied = ~(totalOccupied | certainlyFree)
      probabilisticBoard = ProbabilisticBoard(
        certainBoard = certainBoard,
        certainlyOccupiedByUnknown = certainlyOccupiedByUnknown,
        maybeOccupied = maybeOccupied
      )
    } yield RealisticGuess(probabilisticBoard)
  }
