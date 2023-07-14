package chessfinder

import api.Controller
import sttp.tapir.serverless.aws.sam.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Paths }
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }
import alleycats.std.option

object SamTemplate extends App:

  val organization = "eudemonia"
  val controller   = Controller("async")
  val endpoints    = controller.endpoints

  val jarPath = Paths.get("target/scala-3.3.0/chessfinder-lambda.jar").toAbsolutePath.toString

  val samOptions: AwsSamOptions =
    val source = CodeSource(
      "java17",
      jarPath,
      "chessfinder.LambdaMain::handleRequest"
    )
    AwsSamOptions(
      "Chessfinder",
      source = source,
      memorySize = 1024,
      timeout = 29.seconds
    )
      .withParameter("ChessfinderLambdaRoleArn")
      .customiseOptions { (role, options) =>
        val updatedSource = source.copy(role = Some(role.ref))
        options.copy(source = updatedSource)
      }

  val yaml = AwsSamInterpreter(samOptions).toSamTemplate(endpoints).toYaml
  Files.write(Paths.get(".infrastructure/api.yaml"), yaml.getBytes(UTF_8))
