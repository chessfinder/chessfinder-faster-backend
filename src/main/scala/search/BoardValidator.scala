package chessfinder
package search

import chessfinder.core.ProbabilisticBoard
import search.entity.*
import chessfinder.core.format.SearchFen

trait BoardValidator:

  def validate(board: SearchFen): φ[ProbabilisticBoard]

object BoardValidator:

  class Impl() extends BoardValidator:
    def validate(board: SearchFen): φ[ProbabilisticBoard] = ???

