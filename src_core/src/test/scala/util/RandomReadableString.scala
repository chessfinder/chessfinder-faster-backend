package chessfinder
package util

import scala.util.Random

object RandomReadableString:

  def apply(length: Int): String =
    Iterator
      .continually(Random.nextPrintableChar) // generate random characters
      .filter(_.isLetter)                    // restrict to letters only
      .take(length)                          // take only the required amount
      .mkString

  def apply(): String = RandomReadableString(15)
