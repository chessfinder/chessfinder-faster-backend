package chessfinder
package core

import core.ProbabilisticPiece
import core.ProbabilisticPiece.{ CertainPiece, PartialInformation }
import chess.{ PieceMap, Pos }

type ProbabilisticPieceMap = Map[Pos, ProbabilisticPiece]
type PartialInformationMap = Map[Pos, PartialInformation]

object ProbabilisticPieceMap:

  extension (map: ProbabilisticPieceMap)
    def certain: PieceMap =
      map.collect { case (k, CertainPiece(piece)) => (k, piece) }

    def partial: PartialInformationMap =
      map.collect { case (k, info: PartialInformation) => (k, info) }
