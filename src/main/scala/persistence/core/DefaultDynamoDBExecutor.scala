package chessfinder
package persistence.core

import zio.*
import zio.aws.dynamodb.DynamoDb
import zio.aws.netty
import zio.aws.core.httpclient.HttpClient
import zio.aws.core.config.AwsConfig
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import zio.dynamodb.*

object DefaultDynamoDBExecutor:

  val layer: ZLayer[HttpClient & AwsConfig, Throwable, DynamoDBExecutor] = {
    val dynamoDbConfig =
      ZLayer
        .fromZIO(ZIO.config[DynamoDbConfiguration](DynamoDbConfiguration.config))

    val cutomDynamoDbLayer = dynamoDbConfig.flatMap { dynamoDbConfigEnv =>
      val dynamoDbConfig: DynamoDbConfiguration = dynamoDbConfigEnv.get[DynamoDbConfiguration]
      DynamoDb.customized { builder =>
        builder
          .endpointOverride(dynamoDbConfig.uriValidated)
          .region(dynamoDbConfig.regionValidated)
          .credentialsProvider(DefaultCredentialsProvider.create())
      }
    }

    val dynamoDbLayer: ZLayer[HttpClient & AwsConfig, Throwable, DynamoDb] =
      ZLayer.service[HttpClient] >>> ZLayer.service[AwsConfig] >>> cutomDynamoDbLayer

    val dynamoDbExecutorLayer: ZLayer[HttpClient & AwsConfig, Throwable, DynamoDBExecutor] =
      (dynamoDbLayer ++ ZLayer.succeed(Clock)) >>> DynamoDBExecutor.live // FIXME for what is the clock?
    dynamoDbExecutorLayer
  }
