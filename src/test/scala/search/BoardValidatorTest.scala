package chessfinder
package search

import zio.test.*
import chessfinder.core.SearchFen
import chessfinder.core.ProbabilisticBoard
import search.BrokenLogic.*

import core.SearchFen
object BoardValidatorTest extends ZIOSpecDefault:
  override def spec = suite("BoardValidator")(
    test("method validate should return success if board if valid") {
      val searchFen = SearchFen("-----rk-/-??---bp/--0??-p-/--???---/-0------/----PpP-/--0--PqP/-Q---R-K")
      val actualResult = BoardValidator.validate(searchFen)

      assertZIO(actualResult)(Assertion.isSubtype[ProbabilisticBoard](Assertion.anything))
    },
    
    test("method validate should return falure if board if invalid") {
      val searchFen = SearchFen("")
      val actualResult = BoardValidator.validate(searchFen)

      assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(InvalidSearchBoard)))
    },
  ).provideShared(BoardValidator.Impl.layer)
