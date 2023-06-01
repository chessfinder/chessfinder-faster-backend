package chessfinder
package persistence.core

import persistence.{ PlatformType, SearchStatusType }
import search.entity.*
import sttp.model.Uri

import chess.format.pgn.PgnStr
import zio.dynamodb.{ AttributeValue, ToAttributeValue }
import zio.schema.{ DeriveSchema, Schema }

import java.util.UUID

object DynamoTypeMappers:

  given Schema[UserName]        = Schema[String].transform[UserName](s => UserName(s), _.value)
  given Schema[UserId]          = Schema[String].transform[UserId](s => UserId(s), _.value)
  given Schema[GameId]          = Schema[String].transform(s => GameId(s), _.value)
  given Schema[TaskId]          = Schema[UUID].transform(s => TaskId(s), _.value)
  given Schema[ArchiveId]       = Schema[String].transform(s => ArchiveId(s), _.value)
  given Schema[SearchRequestId] = Schema[UUID].transform(s => SearchRequestId(s), _.value)
  // given Schema[PlatformType] = DeriveSchema.gen[PlatformType]
  given Schema[PlatformType] = Schema[String].transformOrFail(PlatformType.fromString, p => Right(p.toString))
  given Schema[SearchStatusType] =
    Schema[String].transformOrFail(SearchStatusType.fromString, p => Right(p.toString))
  given Schema[ArchiveStatus] = Schema[String].transformOrFail(ArchiveStatus.fromRepr, p => Right(p.repr))
  given Schema[PgnStr]        = Schema[String].transform(s => PgnStr(s), _.value)
  given Schema[Uri]           = Schema[String].transformOrFail(s => Uri.parse(s), u => Right(u.toString))

  given [A](using Schema[A]): ToAttributeValue[A] = (v: A) => AttributeValue.encode[A](v)
