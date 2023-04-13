package chessfinder
package search

import chessfinder.core.ProbabilisticBoard
import search.entity.*
import chessfinder.core.SearchFen
import zio.ZLayer
import chessfinder.search.BrokenLogic.InvalidSearchBoard

import core.SearchFen
trait BoardValidator:

  def validate(board: SearchFen): φ[ProbabilisticBoard]

object BoardValidator:

  def validate(board: SearchFen): ψ[BoardValidator, ProbabilisticBoard] =
    ψ.serviceWithZIO[BoardValidator](_.validate(board))

  class Impl() extends BoardValidator:
    def validate(board: SearchFen): φ[ProbabilisticBoard] =
      SearchFen
        .read(board)
        .fold(_ => φ.fail(InvalidSearchBoard), φ.succeed)

  object Impl:
    val layer = ZLayer.succeed(BoardValidator.Impl())
