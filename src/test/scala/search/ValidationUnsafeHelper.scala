package chess
package search

import cats.data.NonEmptyChain
import cats.kernel.Semigroup
import cats.syntax.*
import chess.search.error.*

trait ValidationUnsafeHelper:
  extension [T](result: ValidationResult[T])
    def get: T = result.fold(_.throwRuntime, identity)

  private given Semigroup[String] = Semigroup.instance[String]((a, b) => s"$a\n$b") 
  extension (errors: NonEmptyChain[ValidationError])
    def throwRuntime: Nothing = throw new RuntimeException(errors.reduce)
