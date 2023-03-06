package chessfinder
package core.error

import cats.data.{NonEmptyChain, Validated}
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal

type β[T] = Validated[BrokenLogics, T]

object β: 

  def valid[T](v: T): β[T] =
    Validated.validNec[BrokenLogic, T](v)

  def invalid[T](e: BrokenLogic): β[T] =
    Validated.invalidNec[BrokenLogic, T](e)

  def fromOption[T](
    option: Option[T],
    ifNone: => BrokenLogic
  ): β[T] = Validated.fromOption(option, ifNone).toValidatedNec

  def fromEither[T](either: Either[BrokenLogic, T]): β[T] =
    Validated.fromEither(either).toValidatedNec

  def fromTry[T](tr: Try[T]): β[T] = 
    tr match 
      case Success(value) => Validated.validNec[BrokenLogic, T](value)
      case Failure(NonFatal(exception)) =>
        Validated.invalidNec[BrokenLogic, T](exception.getMessage)
      case Failure(exception) => throw exception
  
  def fromValidated[T](validated: Validated[BrokenLogic, T]): β[T] = 
    validated.toValidatedNec

  def catchNonfatal[T](effect: => T): β[T] =
    fromTry(Try(effect))

  

object βExt:
   
  extension [OUTPUT](out: OUTPUT)
    def validated: β[OUTPUT] =
      β.valid[OUTPUT](out)

  extension (error: BrokenLogic)
    def failed[OUTPUT]: β[OUTPUT] =
      β.invalid[OUTPUT](error)