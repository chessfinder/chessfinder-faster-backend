package chessfinder
package util

import sttp.model.Uri

import scala.util.Try

// user uri"uri here" string interpolator
@Deprecated
object UriParser:

  def apply(str: String): Try[Uri] =
    Uri.parse(str).left.map(s => RuntimeException(s)).toTry
