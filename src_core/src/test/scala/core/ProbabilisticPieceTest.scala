package chessfinder
package core

import core.Arbitraries.given
import core.ProbabilisticPiece.{ CertainPiece, CertainlyOccupied, ProbablyOccupied }

import cats.syntax.all.*
import chess.bitboard.Bitboard.*
import chess.bitboard.Board
import chess.format.Fen
import chess.{ Piece, Pos }
import munit.Clue.generate
import munit.{ FunSuite, ScalaCheckSuite }
import org.scalacheck.{ Arbitrary, Gen, Prop }

class ProbabilisticPieceTest extends ScalaCheckSuite:

  import scala.language.implicitConversions
  given Conversion[Pos, Int] = _.value

  test("ProbabilisticPiece of the symbol 0 should be CertainlyOccupied") {

    val actualResult = ProbabilisticPiece.fromChar('0')
    assertEquals(ProbabilisticPiece.CertainlyOccupied, actualResult.get)
  }

  test("ProbabilisticPiece of the symbol o should be CertainlyOccupied") {

    val actualResult = ProbabilisticPiece.fromChar('o')
    assertEquals(ProbabilisticPiece.CertainlyOccupied, actualResult.get)
  }

  test("ProbabilisticPiece of the symbol O should be CertainlyOccupied") {

    val actualResult = ProbabilisticPiece.fromChar('O')
    assertEquals(ProbabilisticPiece.CertainlyOccupied, actualResult.get)
  }

  test("ProbabilisticPiece of the symbol ? should be ProbablyOccupied") {

    val actualResult = ProbabilisticPiece.fromChar('?')
    assertEquals(ProbabilisticPiece.ProbablyOccupied, actualResult.get)
  }

  property(
    "ProbabilisticPiece of the any other symbol should be either None if the symbol is unknown or CertainPiece if the symbol is known"
  ) {
    given Gen[Char] = Gen.alphaNumChar.filterNot(ch => ch == '0' | ch == 'o' | ch == 'O' | ch == '?')
    Prop.forAll { (ch: Char) =>
      val expectedResult = Piece.fromChar(ch).map(CertainPiece.apply)
      val actualResult   = ProbabilisticPiece.fromChar(ch)
      assertEquals(actualResult, expectedResult)
    }
  }
