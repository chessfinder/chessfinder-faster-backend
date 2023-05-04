package chessfinder
package api

sealed trait ApiVersion

object ApiVersion:
  case object Newborn extends ApiVersion
  case object Async   extends ApiVersion
