package chessfinder
package pubsub.core

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import zio.*
import zio.aws.core.config.AwsConfig
import zio.aws.core.httpclient.HttpClient
import zio.aws.sqs.Sqs

object DefaultSqsExecutor:

  val layer: ZLayer[HttpClient & AwsConfig, Throwable, Sqs] =
    val sqsConfig =
      ZLayer
        .fromZIO(ZIO.config[SqsConfiguration](SqsConfiguration.config))

    val customSqsLayer = sqsConfig.flatMap { sqsConfigEnv =>
      val sqsConfig: SqsConfiguration = sqsConfigEnv.get[SqsConfiguration]
      Sqs.customized { builder =>
        builder
          .endpointOverride(sqsConfig.uriValidated)
          .region(sqsConfig.regionValidated)
          .credentialsProvider(DefaultCredentialsProvider.create())
      }
    }

    val sqsExecutorLayer: ZLayer[HttpClient & AwsConfig, Throwable, Sqs] =
      (ZLayer.service[HttpClient] ++ ZLayer.succeed(Clock)) >>> ZLayer.service[AwsConfig] >>> customSqsLayer

    sqsExecutorLayer
