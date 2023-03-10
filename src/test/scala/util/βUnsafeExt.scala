package chessfinder
package util

import cats.data.NonEmptyChain
import cats.kernel.Semigroup
import cats.syntax.*
import core.error.*

trait βUnsafeExt:
  extension [T](result: β[T]) def get: T = result.fold(_.throwRuntime, identity)

  private given Semigroup[String] = Semigroup.instance[String]((a, b) => s"$a\n$b")
  extension (errors: NonEmptyChain[ValidationError])
    def throwRuntime: Nothing = throw new RuntimeException(errors.reduce)
