package chessfinder
package client.chess_com

import munit.*
import munit.Clue.generate
import io.circe.parser
import io.circe.Decoder
import sttp.model.Uri
import sttp.model.Uri.UriContext
import testkit.parser.JsonReader
import zio.ConfigProvider
import zio.config.*
import zio.config.typesafe.*
import client.chess_com.ChessDotComClient
import munit.*
import zio.*
import util.ConfigExtension.*

class ChessDotComClientConfigurationTest extends ZSuite:
  testZ("Profile should be parsed correctly") {
    val confAsString =
      """|{
         |  "client": {
         |    "chesscom": {
         |      "baseUrl": "www.example.com/chess"    
         |    }
         |  }
         |}
      """.stripMargin

    val conf           = TypesafeConfigProvider.fromHoconString(confAsString)
    val expectedResult = ChessDotComClient.Impl.Configuration(uri"www.example.com/chess")
    val configLoaded =
      conf.loadTo[ChessDotComClient.Impl.Configuration]

    for actualResult <- configLoaded
    yield assertEquals(expectedResult, actualResult)

  }
