package chessfinder

export ornicar.scalalib.newtypes.*
export ornicar.scalalib.zeros.*
export ornicar.scalalib.extensions.*

import zio.IO

trait BrokenLogic(msg: String)

type φ[T] = IO[BrokenLogic, T]

val φ = zio.ZIO