package chessfinder
package core.format


import ornicar.scalalib.newtypes.*
import ornicar.scalalib.zeros.*
import ornicar.scalalib.extensions.*

opaque type SearchFen = String

object SearchFen extends OpaqueString[SearchFen]
