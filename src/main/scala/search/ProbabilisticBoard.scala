package chess
package search

import cats.syntax.all.*

import chess.bitboard.Bitboard
import chess.bitboard.Bitboard.*
import chess.bitboard.Board
import chess.search.*
import chess.search.BitboardSetOps.⊆
import chess.search.ProbabilisticPiece

case class ProbabilisticBoard(
    certainBoard: Board,
    certainlyOccupiedByUnknown: Bitboard,
    maybeOccupied: Bitboard
):

  val certainlyOccupied: Bitboard = certainlyOccupiedByUnknown | certainBoard.occupied
  val certainlyFree               = ~(certainlyOccupied | maybeOccupied)

  def includes(board: Board): Boolean = {
    certainBoard.pawns ⊆ board.pawns &&
    certainBoard.knights ⊆ board.knights &&
    certainBoard.bishops ⊆ board.bishops &&
    certainBoard.rooks ⊆ board.rooks &&
    certainBoard.queens ⊆ board.queens &&
    certainBoard.kings ⊆ board.kings &&
    certainBoard.white ⊆ board.white &&
    certainBoard.black ⊆ board.black &&
    certainlyOccupied ⊆ board.occupied &&
    certainlyFree ⊆ (~board.occupied)
  }

object ProbabilisticBoard:
  val empty = ProbabilisticBoard(
    certainBoard = Board.empty,
    certainlyOccupiedByUnknown = Bitboard.empty,
    maybeOccupied = Bitboard.empty
  )

  val standard = ProbabilisticBoard(
    certainBoard = Board.standard,
    certainlyOccupiedByUnknown = Bitboard.empty,
    maybeOccupied = Bitboard.empty
  )

  def fromMap(pieces: ProbabilisticPieceMap): ProbabilisticBoard =
    import ProbabilisticPieceMap.*
    val ceratinPieces = pieces.certain
    val partialInfo   = pieces.partial
    val certainBoard  = Board.fromMap(ceratinPieces)

    var certainlyOccupiedByUnknown: Bitboard = Bitboard.empty
    var maybeOccupied                        = Bitboard.empty

    partialInfo.foreach {
      case (s, ProbabilisticPiece.CertainlyOccupied) =>
        certainlyOccupiedByUnknown |= s.bb
      case (s, ProbabilisticPiece.ProbablyOccupied) =>
        maybeOccupied |= s.bb
    }

    ProbabilisticBoard(
      certainBoard = certainBoard,
      certainlyOccupiedByUnknown = certainlyOccupiedByUnknown,
      maybeOccupied = maybeOccupied
    )
