package chessfinder
package testkit.parser

import io.circe.parser
import scala.io.Source

object JsonReader:
  def readResource(resource: String): String =
    val str = Source.fromResource(resource).mkString
    parser
      .parse(str)
      .map(_.spaces2)
      .toTry
      .get
