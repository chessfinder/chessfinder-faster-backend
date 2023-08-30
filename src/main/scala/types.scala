package chessfinder

import java.util.UUID

opaque type UserId = String

object UserId extends OpaqueString[UserId]

opaque type TaskId = UUID

object TaskId extends TotalWrapper[TaskId, UUID]

opaque type SearchRequestId = UUID

object SearchRequestId extends TotalWrapper[SearchRequestId, UUID]

opaque type GameId = String

object GameId extends OpaqueString[GameId]

opaque type UserName = String

object UserName extends OpaqueString[UserName]

opaque type ArchiveId = String

object ArchiveId extends OpaqueString[ArchiveId]
