package chessfinder
package util

import sttp.tapir.ztapir.*

object EndpointCombiner:

  def apply[A, B](
      zs1: ZServerEndpoint[A, Any],
      zs: List[ZServerEndpoint[B, Any]]
  ): List[ZServerEndpoint[A & B, Any]] =
    zs1.widen[A & B] :: zs.map(_.widen[A & B])

  def many[A, B](
      zs1: List[ZServerEndpoint[A, Any]],
      zs2: List[ZServerEndpoint[B, Any]]
  ): List[ZServerEndpoint[A & B, Any]] =
    zs1.foldLeft[List[ZServerEndpoint[A & B, Any]]](zs2.map(_.widen[A & B]))((zs2, e) =>
      EndpointCombiner(e, zs2)
    )
