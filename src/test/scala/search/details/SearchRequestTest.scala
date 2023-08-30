package chessfinder
package search.details

import api.Platform
import search.details
import search.details.SearchRequest

import io.circe.{ parser, Decoder }
import munit.*
import munit.Clue.generate
import sttp.model.Uri
import sttp.model.Uri.UriContext
import zio.json.JsonDecoder

class SearchRequestTest extends FunSuite:
  test("SearchRequest should be parsed correctly") {
    val json = parser
      .parse(
        """|{
           |  "user":"tigran-c-137",
           |  "platform":"chess.com",
           |  "board":"a stupid thing"
           |}
           |""".stripMargin
      )
      .toTry
      .get
    val expectedResult = details.SearchRequest(
      user = "tigran-c-137",
      platform = Platform.`chess.com`,
      board = "a stupid thing"
    )

    val actualResult = Decoder[SearchRequest].decodeJson(json).toTry.get
    assert(expectedResult == actualResult)
  }

  test("SearchRequest should be parsed correctly using ZIO") {
    val json = parser
      .parse(
        """|{
           |  "user":"tigran-c-137",
           |  "platform":"chess.com",
           |  "board":"a stupid thing"
           |}
           |""".stripMargin
      )
      .toTry
      .get
      .noSpaces
    val expectedResult = details.SearchRequest(
      user = "tigran-c-137",
      platform = Platform.`chess.com`,
      board = "a stupid thing"
    )

    val actualResult = JsonDecoder[SearchRequest].decodeJson(json).toOption.get
    assert(expectedResult == actualResult)
  }
