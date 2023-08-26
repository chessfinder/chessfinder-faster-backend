package chessfinder
package client.chess_com.dto

import io.circe.{ parser, Decoder }
import munit.*
import munit.Clue.generate
import sttp.model.Uri
import sttp.model.Uri.UriContext

class ArchivesTest extends FunSuite:
  test("Archives should be parsed correctly") {
    val json = parser
      .parse(
        """|
           |{
           | "archives": [
           |  "https://pub/player/tigran-c-137/games/2022/07",
           |  "https://pub/player/tigran-c-137/games/2022/08",
           |  "https://pub/player/tigran-c-137/games/2022/09",
           |  "https://pub/player/tigran-c-137/games/2022/10",
           |  "https://pub/player/tigran-c-137/games/2022/11"
           |  ]
           |}
           |""".stripMargin
      )
      .toTry
      .get

    val expectedResult = Archives(
      Seq(
        uri"https://pub/player/tigran-c-137/games/2022/07",
        uri"https://pub/player/tigran-c-137/games/2022/08",
        uri"https://pub/player/tigran-c-137/games/2022/09",
        uri"https://pub/player/tigran-c-137/games/2022/10",
        uri"https://pub/player/tigran-c-137/games/2022/11"
      )
    )
    val actualResult = Decoder[Archives].decodeJson(json).toTry.get
    assert(expectedResult == actualResult)
  }
