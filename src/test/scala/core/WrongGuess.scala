package chessfinder
package core

import chess.Color
import chess.bitboard.{ Bitboard, Board }
import org.scalacheck.{ Arbitrary, Gen }
// import org.specs2.control.Properties.aProperty

case class WrongGuess(probabilisticBoard: ProbabilisticBoard)

object WrongGuess:

  enum Piece:
    case King, Queen, Rook, Bishop, Knight, Pawn, Unknown

  private val pieceGen: Gen[Piece] =
    Gen.oneOf(Piece.King, Piece.Queen, Piece.Rook, Piece.Bishop, Piece.Knight, Piece.Pawn, Piece.Unknown)
  private val colorGen: Gen[Color] = Gen.oneOf(Color.White, Color.Black)

  def gen(board: Board): Gen[WrongGuess] = {
    for {
      shift <- Gen.choose[Int](0, 63)
      mask       = 1L << shift
      free       = ~board.occupied
      freeSquare = free & mask
      if freeSquare != Bitboard.empty
      color <- colorGen
      piece <- pieceGen
      certainBoard = modify(color, piece, board, freeSquare)
      certainlyOccupiedByUnknown = piece match
        case Piece.Unknown => freeSquare
        case _             => Bitboard.empty

      probabilisticBoard = ProbabilisticBoard(
        certainBoard = certainBoard,
        certainlyOccupiedByUnknown = certainlyOccupiedByUnknown,
        maybeOccupied = Bitboard.empty
      )
    } yield WrongGuess(probabilisticBoard)
  }

  private def modify(color: Color, piece: Piece, board: Board, freeSquare: Bitboard): Board =
    def ajustColorAndOccupation(color: Color, piece: Piece, freeSquare: Bitboard)(board: Board): Board =
      (piece, color) match
        case (Piece.Unknown, _) => board
        case (_, Color.White) =>
          board.copy(white = board.white | freeSquare, occupied = board.occupied | freeSquare)
        case (_, Color.Black) =>
          board.copy(black = board.black | freeSquare, occupied = board.occupied | freeSquare)

    ajustColorAndOccupation(color, piece, freeSquare) {
      piece match
        case Piece.King =>
          board.copy(kings = board.kings | freeSquare)
        case Piece.Queen =>
          board.copy(queens = board.queens | freeSquare)
        case Piece.Rook =>
          board.copy(rooks = board.rooks | freeSquare)
        case Piece.Bishop =>
          board.copy(bishops = board.bishops | freeSquare)
        case Piece.Knight =>
          board.copy(knights = board.knights | freeSquare)
        case Piece.Pawn =>
          board.copy(pawns = board.pawns | freeSquare)
        case _ => board
    }
