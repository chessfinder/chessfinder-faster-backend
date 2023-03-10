package chessfinder
package core.error

import cats.data.NonEmptyChain
import cats.kernel.Semigroup
import cats.syntax.*

type ValidationError = String

type ValidationErrors = NonEmptyChain[ValidationError]
