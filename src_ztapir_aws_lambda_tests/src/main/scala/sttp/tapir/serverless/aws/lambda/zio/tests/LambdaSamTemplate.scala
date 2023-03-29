package sttp.tapir.serverless.aws.lambda.zio.tests

import sttp.tapir.serverless.aws.sam._

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

object LambdaSamTemplate extends App {

  val jarPath = Paths.get("src_ztapir_aws_lambda_tests/target/scala-3.2.2/ztapir-aws-lambda-tests.jar").toAbsolutePath.toString

  val samOptions: AwsSamOptions = AwsSamOptions(
    "Tests",
    source = CodeSource(
      "java11",
      jarPath,
      "sttp.tapir.serverless.aws.lambda.zio.tests.ZIOLambdaHandlerV2::handleRequest"
    ),
    memorySize = 1024
  )
  val yaml = AwsSamInterpreter(samOptions).toSamTemplate(all.allEndpoints.map(_.endpoint).toList).toYaml
  Files.write(Paths.get("template.yaml"), yaml.getBytes(UTF_8))
}
