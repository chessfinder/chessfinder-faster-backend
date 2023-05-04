package chessfinder
package persistence.core

import zio.dynamodb.{ AttributeValue, ToAttributeValue }
import zio.schema.{ DeriveSchema, Schema }
import java.util.UUID
import chessfinder.search.entity.{ GameId, TaskId, UserId, UserName }
import persistence.PlatformType
import chess.format.pgn.PgnStr
import sttp.model.Uri

object DynamoTypeMappers:

  given Schema[UserName] = Schema[String].transform[UserName](s => UserName(s), _.value)
  given Schema[UserId]   = Schema[String].transform[UserId](s => UserId(s), _.value)
  given Schema[GameId]   = Schema[String].transform(s => GameId(s), _.value)
  given Schema[TaskId]   = Schema[UUID].transform(s => TaskId(s), _.value)
  // given Schema[PlatformType] = DeriveSchema.gen[PlatformType]
  given Schema[PlatformType] = Schema[String].transformOrFail(PlatformType.fromString, p => Right(p.toString))
  given Schema[PgnStr]       = Schema[String].transform(s => PgnStr(s), _.value)
  given Schema[Uri]          = Schema[String].transformOrFail(s => Uri.parse(s), u => Right(u.toString))

  given [A](using Schema[A]): ToAttributeValue[A] = (v: A) => AttributeValue.encode[A](v)
