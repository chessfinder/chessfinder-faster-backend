package chessfinder
package core.error

import cats.data.NonEmptyChain
import cats.kernel.Semigroup
import cats.syntax.*

type BrokenLogic = String

type BrokenLogics = NonEmptyChain[BrokenLogic]
