package chess
package search.error

import cats.data.{NonEmptyChain, Validated}
import search.error.ValidationError
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal

type ValidationResult[T] = Validated[ValidationErrors, T]

object ValidationResult: 

  def valid[T](v: T): ValidationResult[T] =
    Validated.validNec[ValidationError, T](v)

  def invalid[T](e: ValidationError): ValidationResult[T] =
    Validated.invalidNec[ValidationError, T](e)

  def fromOption[T](
    option: Option[T],
    ifNone: => ValidationError
  ): ValidationResult[T] = Validated.fromOption(option, ifNone).toValidatedNec

  def fromEither[T](either: Either[ValidationError, T]): ValidationResult[T] =
    Validated.fromEither(either).toValidatedNec

  def fromTry[T](tr: Try[T]): ValidationResult[T] = 
    tr match 
      case Success(value) => Validated.validNec[ValidationError, T](value)
      case Failure(NonFatal(exception)) =>
        Validated.invalidNec[ValidationError, T](exception.getMessage)
      case Failure(exception) => throw exception
  
  def fromValidated[T](validated: Validated[ValidationError, T]): ValidationResult[T] = 
    validated.toValidatedNec

  def catchNonfatal[T](effect: => T): ValidationResult[T] =
    fromTry(Try(effect))

  

object ValidationResultExt:
   
  extension [OUTPUT](out: OUTPUT)
    def validated: ValidationResult[OUTPUT] =
      ValidationResult.valid[OUTPUT](out)

  extension (error: ValidationError)
    def failed[OUTPUT]: ValidationResult[OUTPUT] =
      ValidationResult.invalid[OUTPUT](error)