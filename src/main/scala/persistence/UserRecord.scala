package chessfinder
package persistence

import persistence.core.{ DynamoTable, DynamoTypeMappers }
import search.entity.{ UserId, UserIdentified, UserName }

import zio.schema.{ DeriveSchema, Schema }

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
