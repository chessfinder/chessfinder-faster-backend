package chessfinder
package search

import zio.{ZIO, IO}
import search.entity.UserName
import sttp.model.Uri

trait BrokenLogic(val msg: String)
object BrokenLogic:
  case object InvalidSearchBoard extends BrokenLogic(s"Invalid board!")
  case class ProfileNotFound(userName: UserName) extends BrokenLogic(s"Profile $userName not found!")
  case class NoGameAvaliable(userName: UserName) extends BrokenLogic(s"Profile $userName does not have any information about their played games!")

type φ[T] = IO[BrokenLogic, T]
type ψ[R, T] = ZIO[R, BrokenLogic, T]

val φ = zio.ZIO
val ψ = zio.ZIO



