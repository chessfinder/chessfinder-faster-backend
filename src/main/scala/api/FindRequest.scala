package chessfinder
package api

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import search.entity.ChessPlatform

final case class FindRequest(
  user: String,
  platform: Platform,
  board: String
)

object FindRequest:
  given Codec[FindRequest] = deriveCodec[FindRequest]
  given Schema[FindRequest] = Schema.derived[FindRequest]

enum Platform:
  case ChessDotCom

  def toPlatform = this match
    case ChessDotCom => ChessPlatform.ChessDotCom
  

object Platform:
  given Codec[Platform] = deriveCodec[Platform]
  given Schema[Platform] = Schema.derivedEnumeration.defaultStringBased
