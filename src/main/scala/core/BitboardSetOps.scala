package chessfinder
package core

import chess.bitboard.Bitboard

object BitboardSetOps:
  extension (b: Bitboard)
    inline infix def ⊆(inline widerBoard: Bitboard): Boolean = ((widerBoard & b) ^ b) == Bitboard.empty
