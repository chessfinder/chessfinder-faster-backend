package chessfinder
package testkit.parser

import io.circe.parser
import scala.io.Source
import io.circe.Json

object JsonReader:
  def readResource(resource: String): String =
    parseResource(resource).spaces2

  def parseResource(resource: String): Json =
    val str = Source.fromResource(resource).mkString
    parser
      .parse(str)
      .toTry
      .get