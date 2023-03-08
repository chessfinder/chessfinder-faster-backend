package chessfinder
package client.chess_com.dto

import core.error.ValidationError
import chessfinder.search.entity.UserName


object errors:
  case class ProfileNotFound(userName: UserName) extends BrokenLogic(s"Profile $userName not found!")
  case object ServiceIsOverloaded extends BrokenLogic("Service is overloaded. Try later.")