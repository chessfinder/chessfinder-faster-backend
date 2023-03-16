package chessfinder
package search

import chessfinder.core.ProbabilisticBoard
import search.entity.*
import chessfinder.core.format.SearchFen
import zio.ZLayer

trait BoardValidator:

  def validate(board: SearchFen): φ[ProbabilisticBoard]

object BoardValidator:

  def validate(board: SearchFen): ψ[BoardValidator, ProbabilisticBoard] =
    ψ.serviceWithZIO[BoardValidator](_.validate(board))

  class Impl() extends BoardValidator:
    def validate(board: SearchFen): φ[ProbabilisticBoard] = ???

  object Impl:
    val layer = ZLayer.succeed(BoardValidator.Impl())

