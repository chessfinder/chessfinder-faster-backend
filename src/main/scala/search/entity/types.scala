package chessfinder
package search.entity

import java.util.UUID

opaque type UserId = String

object UserId extends OpaqueString[UserId]

opaque type TaskId = UUID

object TaskId extends TotalWrapper[TaskId, UUID]

opaque type GameId = String

object GameId extends OpaqueString[GameId]

opaque type UserName = String

object UserName extends OpaqueString[UserName]
