package chessfinder

final case class User(platform: ChessPlatform, userName: UserName):
  def identified(userId: UserId): UserIdentified =
    UserIdentified(platform = platform, userName = userName, userId = userId)
