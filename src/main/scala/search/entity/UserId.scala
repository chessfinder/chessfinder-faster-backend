package chessfinder
package search.entity

opaque type UserId = java.util.UUID

object UserId extends TotalWrapper[java.util.UUID, UserId]
