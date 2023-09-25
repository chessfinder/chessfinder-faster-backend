package chessfinder
package util

import core.Walidated

import cats.data.NonEmptyChain
import cats.kernel.Semigroup

trait WalidatedUnsafeExt:
  extension [T](result: Walidated[T]) def get: T = result.fold(_.throwRuntime, identity)

  private given Semigroup[String] = Semigroup.instance[String]((a, b) => s"$a\n$b")
  extension (errors: NonEmptyChain[Walidated.ValidationError])
    def throwRuntime: Nothing = throw new RuntimeException(errors.reduce)
