package chess
package search

trait DescriptionHelper:

  extension (str: String)
    def aline: String = str.replace('\n', ' ')
