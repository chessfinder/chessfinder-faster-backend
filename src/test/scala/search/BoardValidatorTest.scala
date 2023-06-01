package chessfinder
package search

import core.{ ProbabilisticBoard, SearchFen }
import search.BrokenLogic.*

import zio.ZIO
import zio.test.*
object BoardValidatorTest extends ZIOSpecDefault:

  val service = ZIO.service[BoardValidator]
  override def spec = suite("BoardValidator")(
    test("method validate should return success if board if valid") {
      val searchFen    = SearchFen("-----rk-/-??---bp/--0??-p-/--???---/-0------/----PpP-/--0--PqP/-Q---R-K")
      val actualResult = service.flatMap(_.validate(searchFen))

      assertZIO(actualResult)(Assertion.isSubtype[ProbabilisticBoard](Assertion.anything))
    },
    test("method validate should return falure if board if invalid") {
      val searchFen    = SearchFen("")
      val actualResult = service.flatMap(_.validate(searchFen))

      assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(InvalidSearchBoard)))
    }
  ).provideShared(BoardValidator.Impl.layer)
