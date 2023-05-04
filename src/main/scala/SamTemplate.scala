package chessfinder

import sttp.tapir.serverless.aws.sam.*
import chessfinder.api.{ AsyncController, SyncController }
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Paths }
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }

object SamTemplate extends App:

  val organization    = "eudemonia"
  val syncController  = SyncController("newborn")
  val asyncController = AsyncController("async")
  val endpoints       = syncController.endpoints ++ asyncController.endpoints

  val jarPath = Paths.get("target/scala-3.2.2/chessfinder-lambda.jar").toAbsolutePath.toString

  val samOptions: AwsSamOptions = AwsSamOptions(
    "Chessfinder",
    source = CodeSource(
      "java11",
      jarPath,
      "chessfinder.LambdaMain::handleRequest"
    ),
    memorySize = 1024,
    timeout = 29.seconds
  )
  val yaml = AwsSamInterpreter(samOptions).toSamTemplate(endpoints).toYaml
  Files.write(Paths.get(".infrastructure/api.yaml"), yaml.getBytes(UTF_8))
