package chessfinder
package api

import munit.*
import munit.Clue.generate
import io.circe.parser
import io.circe.Decoder
import sttp.model.Uri
import sttp.model.Uri.UriContext
import chessfinder.api.FindRequest
import chessfinder.api.Platform
import zio.json.JsonDecoder

class FindRequestTest extends FunSuite:
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
    val expectedResult = FindRequest(
      user = "tigran-c-137",
      platform = Platform.`chess.com`,
      board = "a stupid thing"
    )

    val actualResult = Decoder[FindRequest].decodeJson(json).toTry.get
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
    val expectedResult = FindRequest(
      user = "tigran-c-137",
      platform = Platform.`chess.com`,
      board = "a stupid thing"
    )

    val actualResult = JsonDecoder[FindRequest].decodeJson(json).toOption.get
    assert(expectedResult == actualResult)
  }
