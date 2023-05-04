package chessfinder
package persistence

import persistence.core.DynamoTable
import persistence.core.DynamoTypeMappers
import zio.schema.{ DeriveSchema, Schema }
import search.entity.{ UserId, UserIdentified, UserName }

case class UserRecord(user_name: UserName, platform: PlatformType, user_id: UserId):
  def toUser: UserIdentified =
    UserIdentified(
      platform = platform.toPlatform,
      userName = user_name,
      userId = user_id
    )

object UserRecord:

  import DynamoTypeMappers.given

  given Schema[UserRecord] = DeriveSchema.gen[UserRecord]

  object Table
      extends DynamoTable.SortedSeq.Impl[UserName, PlatformType, UserRecord](
        name = "users",
        partitionKeyName = "user_name",
        sortKeyName = "platform"
      )

  def fromUserIdentified(user: UserIdentified): UserRecord =
    UserRecord(
      platform = PlatformType.fromPlatform(user.platform),
      user_name = user.userName,
      user_id = user.userId
    )
