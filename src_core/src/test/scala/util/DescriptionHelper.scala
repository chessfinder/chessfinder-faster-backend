package chessfinder
package util

trait DescriptionHelper:

  extension (str: String) def aline: String = str.replace('\n', ' ')
