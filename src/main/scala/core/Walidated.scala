package chessfinder
package core

import cats.data.{ NonEmptyChain, Validated }

type Walidated[T] = Validated[Walidated.ValidationErrors, T]

object Walidated:

  type ValidationError = String

  type ValidationErrors = NonEmptyChain[ValidationError]

  def valid[T](v: T): Walidated[T] =
    Validated.validNec[ValidationError, T](v)

  def invalid[T](e: ValidationError): Walidated[T] =
    Validated.invalidNec[ValidationError, T](e)

  def fromValidated[T](validated: Validated[ValidationError, T]): Walidated[T] =
    validated.toValidatedNec

  object Ext:
    extension [OUTPUT](out: OUTPUT)
      def validated: Walidated[OUTPUT] =
        Walidated.valid[OUTPUT](out)

    extension (error: ValidationError)
      def failed[OUTPUT]: Walidated[OUTPUT] =
        Walidated.invalid[OUTPUT](error)
