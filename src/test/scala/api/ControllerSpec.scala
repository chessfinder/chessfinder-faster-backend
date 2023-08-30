package chessfinder
package api

import com.typesafe.config.ConfigFactory
import sttp.tapir.server.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.*
import zio.http.*

object ControllerSpec extends Mocks:

  val version    = "async"
  val blueprint  = Controller(version)
  val controller = Controller.Impl(blueprint)

  private val config      = ConfigFactory.load()
  private val configLayer = ZLayer.succeed(config)

  private val zioInterpreter = ZioHttpInterpreter()

  private val endpoints =
    controller.rest

  val app =
    zioInterpreter.toHttp(endpoints).withDefaultErrorResponse

  protected lazy val clientLayer = Client.default.orDie

  // def run(controllerLayer: ULayer[GameFinder]) =
  //   Server
  //     .serve(app)
  //     .provide(
  //       Server.default,
  //       controllerLayer
  //     )

  // don't know how to write a test for the controller.
