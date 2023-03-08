package chessfinder
package search

import chessfinder.core.ProbabilisticBoard
import search.entity.*

trait BoardValidator:

  def validate(board: RawBoard): φ[ProbabilisticBoard]
