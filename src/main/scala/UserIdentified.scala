package chessfinder

final case class UserIdentified(platform: ChessPlatform, userName: UserName, userId: UserId):
  def user: User = User(platform, userName)
