package chessfinder
package search

import zio.test.*
import chessfinder.core.format.SearchFenReader
import chessfinder.core.format.SearchFen
import chessfinder.core.ProbabilisticBoard
import search.BrokenLogic.*

class BoardValidatorTest extends ZIOSpecDefault:

  val service = BoardValidator.Impl()

  override def spec = suite("BoardValidator")(
    test("method validate should return success if board if valid") {
      val searchFen = SearchFen("")

      val expectedResult = ProbabilisticBoard(
        certainBoard = ???,
        certainlyOccupiedByUnknown = ???,
        maybeOccupied = ???
      )

      val actualResult = service.validate(searchFen)

      assertZIO(actualResult)(Assertion.equalTo(expectedResult))

    },
    
    test("method validate should return falure if board if invalid") {
      val searchFen = SearchFen("")

      val expectedResult = ProbabilisticBoard(
        certainBoard = ???,
        certainlyOccupiedByUnknown = ???,
        maybeOccupied = ???
      )

      val actualResult = service.validate(searchFen)

      assertZIO(actualResult.exit)(Assertion.fails(Assertion.equalTo(InvalidSearchBoard)))

    },
  )
