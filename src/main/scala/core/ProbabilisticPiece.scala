package chessfinder
package core

import chess.{Piece, Color, Role}

sealed trait ProbabilisticPiece

object ProbabilisticPiece:
  case class CertainPiece(piece: Piece) extends ProbabilisticPiece
  object CertainPiece:
    def apply(color: Color, role: Role): CertainPiece = 
      CertainPiece(Piece(color, role))
  
  sealed trait PartialInformation extends ProbabilisticPiece
  case object CertainlyOccupied   extends PartialInformation
  case object ProbablyOccupied    extends PartialInformation

  def fromChar(c: Char): Option[ProbabilisticPiece] =
    c match
      case 'o' | 'O' | '0' => Some(CertainlyOccupied)
      case '?'             => Some(ProbablyOccupied)
      case c               => Piece.fromChar(c).map(CertainPiece.apply)
