package chessfinder
package search

import search.entity.*
import sttp.model.Uri

import zio.{ IO, ZIO }

sealed trait BrokenLogic(val msg: String)
object BrokenLogic:
  case object InvalidSearchBoard extends BrokenLogic(s"Invalid board!")
  case object InvalidGame        extends BrokenLogic(s"Invalid game!")
  case class ProfileNotFound(user: User)
      extends BrokenLogic(s"Profile ${user.userName} from ${user.platform.toString} not found!")
  case class NoGameAvailable(user: User)
      extends BrokenLogic(
        s"Profile ${user.userName} from ${user.platform.toString} does not have any information about their played games!"
      )
  case object ServiceOverloaded extends BrokenLogic("Service is overloaded. Try later.")
  case class ProfileIsNotCached(user: User)
      extends BrokenLogic(s"Profile ${user.userName} from ${user.platform.toString} is not chached!")
  case class TaskProgressOverflown(taskId: TaskId)
      extends BrokenLogic(s"Task ${taskId.toString} progerss is overflown")
  case class TaskNotFound(taskId: TaskId) extends BrokenLogic(s"Task ${taskId.toString} not found")
  case class SearchResultNotFound(searchId: SearchRequestId)
      extends BrokenLogic(s"Task ${searchId.toString} not found")
  case class ArchiveNotFound(archiveId: ArchiveId)
      extends BrokenLogic(s"Archive ${archiveId.value} not found")
  case class UndefinedArchive(resource: Uri)
      extends BrokenLogic(s"Archive resource ${resource.toString} does not contain archive period")

type φ[T] = IO[BrokenLogic, T]
