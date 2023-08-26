package chessfinder
package api

import api.{ Platform, SearchRequest }

import io.circe.{ parser, Decoder }
import munit.*
import munit.Clue.generate
import sttp.model.Uri
import sttp.model.Uri.UriContext
import zio.json.JsonDecoder

class SearchRequestTest extends FunSuite:
  test("FindRequest should be parsed correctly") {
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
    val expectedResult = SearchRequest(
      user = "tigran-c-137",
      platform = Platform.`chess.com`,
      board = "a stupid thing"
    )

    val actualResult = Decoder[SearchRequest].decodeJson(json).toTry.get
    assert(expectedResult == actualResult)
  }

  test("FindRequest should be parsed correctly using ZIO") {
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
    val expectedResult = SearchRequest(
      user = "tigran-c-137",
      platform = Platform.`chess.com`,
      board = "a stupid thing"
    )

    val actualResult = JsonDecoder[SearchRequest].decodeJson(json).toOption.get
    assert(expectedResult == actualResult)
  }
