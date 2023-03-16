package chessfinder
package client

import chessfinder.search.entity.UserName
import zio.{IO, ZIO}
import sttp.model.Uri

trait ClientError(val msg: String)

object ClientError:
  case class ProfileNotFound(userName: UserName) extends ClientError(s"Profile $userName not found!")
  case class ArchiveNotFound(resource: Uri) extends ClientError(s"Archive $resource not found!")
  case object SomethingWentWrong extends ClientError("Something went wrong!")

type μ[T] = IO[ClientError, T]
type κ[R, T] = ZIO[R, ClientError, T]

val μ = ZIO
val κ = ZIO
