package chessfinder

import sttp.tapir.serverless.aws.sam.*
import chessfinder.api.ControllerBlueprint
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

object SamTemplate extends App {

  val organization = "eudemonia"
  val version    = "newborn"
  val blueprint = ControllerBlueprint(version)

  val jarPath = Paths.get("target/scala-3.2.2/chessfinder-lambda.jar").toAbsolutePath.toString

  val samOptions: AwsSamOptions = AwsSamOptions(
    "Tests",
    source = CodeSource(
      "java11",
      jarPath,
      "chessfinder.LambdaMain::handleRequest"
    ),
    memorySize = 1024,
    timeout = 180.seconds
  )
  val yaml = AwsSamInterpreter(samOptions).toSamTemplate(blueprint.endpoints).toYaml
  Files.write(Paths.get("template.yaml"), yaml.getBytes(UTF_8))
}
