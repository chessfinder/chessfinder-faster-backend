package chessfinder
package pubsub.core

import zio.aws.core.config.CommonAwsConfig
import zio.aws.sqs.Sqs
import software.amazon.awssdk.auth.credentials.{ AwsBasicCredentials, StaticCredentialsProvider }
import software.amazon.awssdk.regions.Region
import zio.sqs.producer.{ Producer, ProducerEvent }
import zio.sqs.serialization.Serializer
import zio.sqs.{ SqsStream, SqsStreamSettings, Utils }
import zio.*
import zio.*
import zio.aws.dynamodb.DynamoDb
import zio.aws.netty
import zio.aws.core.httpclient.HttpClient
import zio.aws.core.config.AwsConfig
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

object DefaultSqsExecutor:

  val layer: ZLayer[HttpClient & AwsConfig, Throwable, Sqs] =
    val sqsConfig =
      ZLayer
        .fromZIO(ZIO.config[SqsConfiguration](SqsConfiguration.config))

    val cutomSqsLayer = sqsConfig.flatMap { sqsConfigEnv =>
      val sqsConfig: SqsConfiguration = sqsConfigEnv.get[SqsConfiguration]
      Sqs.customized { builder =>
        builder
          .endpointOverride(sqsConfig.uriValidated)
          .region(sqsConfig.regionValidated)
          .credentialsProvider(DefaultCredentialsProvider.create())
      }
    }

    val sqsExecutorLayer: ZLayer[HttpClient & AwsConfig, Throwable, Sqs] =
      (ZLayer.service[HttpClient] ++ ZLayer.succeed(Clock)) >>> ZLayer.service[AwsConfig] >>> cutomSqsLayer

    sqsExecutorLayer
