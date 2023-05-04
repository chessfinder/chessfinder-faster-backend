package chessfinder
package search.entity

import search.entity.ChessPlatform

final case class User(platform: ChessPlatform, userName: UserName):
  def identified(userId: UserId): UserIdentified =
    UserIdentified(platform = platform, userName = userName, userId = userId)

final case class UserIdentified(platform: ChessPlatform, userName: UserName, userId: UserId):
  def user: User = User(platform, userName)
