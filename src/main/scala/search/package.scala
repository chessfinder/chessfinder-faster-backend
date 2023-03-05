package chess
package search

export ornicar.scalalib.newtypes.*
export ornicar.scalalib.zeros.*
export ornicar.scalalib.extensions.*

import search.ProbabilisticPiece
import search.ProbabilisticPiece.{ CertainPiece, PartialInformation }

type ProbabilisticPieceMap = Map[Pos, ProbabilisticPiece]
type PartialInformationMap = Map[Pos, PartialInformation]

object ProbabilisticPieceMap:

  extension (map: ProbabilisticPieceMap)
    def certain: PieceMap =
      map.collect { case (k, CertainPiece(piece)) => (k, piece) }

    def partial: PartialInformationMap =
      map.collect { case (k, info: PartialInformation) => (k, info) }
