package chessfinder

import zio.ZIOApp
import zio.ZIOAppDefault

import sttp.tapir.ztapir.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.http.{HttpApp, Request, Response}
import zio.*
import zio.http.*
import chessfinder.api.Controller
import chessfinder.search.GameFinder

object Main extends ZIOAppDefault:

  val version = "newborn"
  val controller = Controller(version)
    
  val app =
    ZioHttpInterpreter().toHttp(controller.rest).withDefaultErrorResponse 

  def run = Server.serve(app).provide(Server.default ++ GameFinder.Impl.layer)

