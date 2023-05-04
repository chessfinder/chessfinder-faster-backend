package chessfinder
package persistence.core

import zio.ZIO
import zio.dynamodb.DynamoDBQuery.*
import zio.dynamodb.KeyConditionExpression.*
import zio.dynamodb.UpdateExpression.Action
import zio.dynamodb.*
import zio.schema.Schema
import zio.stream.ZSink
import zio.stream.ZStream

sealed trait DynamoTable:

  def name: String

  def partitionKeyName: String

object DynamoTable:

  type MaybeValue[T] = Either[DynamoDBError, T]

  trait Unique[PartitionKey, V] extends DynamoTable:
    def get[T: Schema](
        partitionKey: PartitionKey,
        projections: ProjectionExpression[?, ?]*
    ): ZIO[DynamoDBExecutor, Throwable, MaybeValue[T]]
    def put(value: V): ZIO[DynamoDBExecutor, Throwable, Unit]
    def update(key: PrimaryKey)(action: Action[V]): ZIO[DynamoDBExecutor, Throwable, Unit]

  object Unique:
    open class Impl[PartitionKey: ToAttributeValue, V: Schema](
        val name: String,
        val partitionKeyName: String
    ) extends Unique[PartitionKey, V]:

      override def get[T: Schema](
          partitionKey: PartitionKey,
          projections: ProjectionExpression[?, ?]*
      ): ZIO[DynamoDBExecutor, Throwable, MaybeValue[T]] =
        DynamoDBQuery.get[T](name, PrimaryKey(partitionKeyName -> partitionKey), projections*).execute

      override def put(value: V): ZIO[DynamoDBExecutor, Throwable, Unit] =
        DynamoDBQuery.put(name, value).execute.unit

      override def update(key: PrimaryKey)(action: Action[V]): ZIO[DynamoDBExecutor, Throwable, Unit] =
        DynamoDBQuery.updateItem(name, key: PrimaryKey)(action: Action[V]).execute.unit

  trait SortedSeq[PartitionKey, SortKey, V] extends DynamoTable:

    def sortKeyName: String

    def list[T: Schema](
        partitionKeyValue: PartitionKey,
        projections: ProjectionExpression[?, ?]*
    ): ZIO[DynamoDBExecutor, Throwable, Seq[T]]

    def get[T: Schema](
        partitionKeyValue: PartitionKey,
        sortKeyValue: SortKey,
        projections: ProjectionExpression[?, ?]*
    ): ZIO[DynamoDBExecutor, Throwable, MaybeValue[T]]

    def put(value: V): ZIO[DynamoDBExecutor, Throwable, Unit]

    def putMany(values: V*): ZIO[DynamoDBExecutor, Throwable, Unit]

    def update(partitionKeyValue: PartitionKey, sortKeyValue: SortKey)(
        action: Action[V]
    ): ZIO[DynamoDBExecutor, Throwable, Unit]

    def delete(partitionKeyValue: PartitionKey, sortKeyValue: SortKey): ZIO[DynamoDBExecutor, Throwable, Unit]

  object SortedSeq:
    open class Impl[PartitionKey: ToAttributeValue, SortKey: ToAttributeValue, V: Schema](
        val name: String,
        val partitionKeyName: String,
        val sortKeyName: String
    ) extends SortedSeq[PartitionKey, SortKey, V]:

      override def get[T: Schema](
          partitionKeyValue: PartitionKey,
          sortKeyValue: SortKey,
          projections: ProjectionExpression[?, ?]*
      ): ZIO[DynamoDBExecutor, Throwable, MaybeValue[T]] =
        DynamoDBQuery
          .get[T](
            name,
            PrimaryKey.apply(partitionKeyName -> partitionKeyValue, sortKeyName -> sortKeyValue),
            projections*
          )
          .execute

      override def put(value: V): ZIO[DynamoDBExecutor, Throwable, Unit] =
        DynamoDBQuery.put[V](name, value).execute.unit

      override def delete(
          partitionKeyValue: PartitionKey,
          sortKeyValue: SortKey
      ): ZIO[DynamoDBExecutor, Throwable, Unit] =
        DynamoDBQuery
          .deleteItem(
            name,
            PrimaryKey.apply(partitionKeyName -> partitionKeyValue, sortKeyName -> sortKeyValue)
          )
          .execute
          .unit

      override def list[T: Schema](
          partitionKeyValue: PartitionKey,
          projections: ProjectionExpression[?, ?]*
      ): ZIO[DynamoDBExecutor, Throwable, Seq[T]] =
        queryAll(name, projections*)(Schema[T])
          .whereKey(partitionKey(partitionKeyName) === partitionKeyValue)
          .execute
          .flatMap(_.runCollect)
          .map(_.toList)

      override def update(partitionKeyValue: PartitionKey, sortKeyValue: SortKey)(
          action: Action[V]
      ): ZIO[DynamoDBExecutor, Throwable, Unit] =
        DynamoDBQuery
          .updateItem(
            name,
            PrimaryKey.apply(partitionKeyName -> partitionKeyValue, sortKeyName -> sortKeyValue)
          )(action)
          .execute
          .unit

      override def putMany(values: V*): ZIO[DynamoDBExecutor, Throwable, Unit] =
        batchWriteFromStream(ZStream.fromIterable(values))(value =>
          DynamoDBQuery.put[V](name, value)
        ).runCollect.unit
