package chessfinder
package search

import zio.{ IO, ZIO }
import search.entity.{ User, UserName }
import sttp.model.Uri
import chessfinder.search.entity.TaskId

sealed trait BrokenLogic(val msg: String)
object BrokenLogic:
  case object InvalidSearchBoard extends BrokenLogic(s"Invalid board!")
  case object InvalidGame        extends BrokenLogic(s"Invalid game!")
  case class ProfileNotFound(user: User)
      extends BrokenLogic(s"Profile ${user.userName} from ${user.platform.toString} not found!")
  case class NoGameAvaliable(user: User)
      extends BrokenLogic(
        s"Profile ${user.userName} from ${user.platform.toString} does not have any information about their played games!"
      )
  case object ServiceOverloaded extends BrokenLogic("Service is overloaded. Try later.")
  case class ProfileIsNotCached(user: User)
      extends BrokenLogic(s"Profile ${user.userName} from ${user.platform.toString} is not chached!")
  case class TaskProgressOverflown(taskId: TaskId)
      extends BrokenLogic(s"Task ${taskId.toString} progerss is overflown")
  case class TaskNotFound(taskId: TaskId) extends BrokenLogic(s"Task ${taskId.toString} not found")

type φ[T]    = IO[BrokenLogic, T]
type ψ[R, T] = ZIO[R, BrokenLogic, T]

val φ = zio.ZIO
val ψ = zio.ZIO
