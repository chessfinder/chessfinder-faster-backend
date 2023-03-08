package chessfinder
package client.chess_com.dto

import munit.*
import munit.Clue.generate
import io.circe.parser
import io.circe.Decoder
import sttp.model.Uri
import sttp.model.Uri.UriContext

class ProfileTest extends FunSuite:
  test("Profile should be parsed correctly") {
    val json = parser.parse(
      """|
         |{
         |  "player_id": 191338281,
         |  "@id": "https://api.chess.com/pub/player/tigran-c-137",
         |  "url": "https://www.chess.com/member/tigran-c-137",
         |  "username": "tigran-c-137",
         |  "followers": 10,
         |  "country": "https://api.chess.com/pub/country/AM",
         |  "last_online": 1678264516,
         |  "joined": 1658920370,
         |  "status": "premium",
         |  "is_streamer": false,
         |  "verified": false,
         |  "league": "Champion"
         |}
         |""".stripMargin
    ).toTry.get
    val expectedResult = Profile(uri"https://www.chess.com/member/tigran-c-137")  
    val actualResult = Decoder[Profile].decodeJson(json).toTry.get 
    assert(expectedResult == actualResult)     
  }
