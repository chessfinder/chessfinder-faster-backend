package chessfinder
package testkit

import persistence.core.DefaultDynamoDBExecutor
import pubsub.core.*
import search.entity.*
import testkit.NarrowIntegrationSuite

import munit.FunSuite
import zio.*
import zio.aws.core.config.AwsConfig
import zio.aws.netty
import zio.aws.sqs.Sqs
import zio.config.typesafe.TypesafeConfigProvider
import zio.dynamodb.*
import zio.test.*

abstract class NarrowIntegrationSuite extends ZIOSpecDefault:

  InitIntegrationEnv.Narrow.run

  val configLayer   = InitIntegrationEnv.Narrow.configLayer
  val loggingLayer  = InitIntegrationEnv.Narrow.loggingLayer
  val dynamodbLayer = InitIntegrationEnv.Narrow.dynamodbLayer
  val sqsLayer      = InitIntegrationEnv.Narrow.sqsLayer

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    configLayer >+> loggingLayer ++ testEnvironment
