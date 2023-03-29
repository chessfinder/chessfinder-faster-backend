// package chessfinder

// import zio.ZIOApp
// import zio.ZIOAppDefault

// import sttp.tapir.ztapir.*
// import sttp.tapir.server.ziohttp.ZioHttpInterpreter
// import zio.http.{ HttpApp, Request, Response }
// import zio.*
// import zio.http.{App as _, *}
// import chessfinder.api.Controller
// import chessfinder.search.GameFinder
// import zio.Console.ConsoleLive
// import sttp.apispec.openapi.Server as OAServer
// import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
// import sttp.tapir.swagger.*
// import sttp.tapir.redoc.*
// import sttp.tapir.redoc.RedocUIOptions
// import sttp.apispec.openapi.circe.yaml.*
// import sttp.tapir.server.*
// import chessfinder.search.BoardValidator
// import chessfinder.search.GameDownloader
// import chessfinder.search.Searcher
// import chessfinder.client.chess_com.ChessDotComClient
// import com.typesafe.config.ConfigFactory
// import sttp.tapir.serverless.aws.lambda.LambdaHandler

// import cats.effect.unsafe.implicits.global
// import com.amazonaws.services.lambda.runtime.Context
// import sttp.tapir.server.ServerEndpoint
// import sttp.tapir.serverless.aws.lambda.{AwsRequest, LambdaHandler}
// import java.io.{InputStream, OutputStream}
// import cats.implicits.*
// import zio.interop.catz.*

// object JvmMain extends LambdaHandler[Task, AwsRequest]:

//   val organization = "eudemonia"
//   val version    = "newborn"
//   val controller = Controller(version)

//   private val swaggerHost: String = s"http://localhost:8080"

//   private val config = ConfigFactory.load()
//   private val configLayer = ZLayer.succeed(config)

//   private val servers: List[OAServer] = List(OAServer(swaggerHost).description("Admin"))
//   private val docsAsYaml: String = OpenAPIDocsInterpreter()
//     .toOpenAPI(controller.endpoints, "ChessFinder", "newborn")
//     .servers(servers)
//     .toYaml

//   private val zioInterpreter = ZioHttpInterpreter()
//   private val swaggerEndpoint: List[ZServerEndpoint[GameFinder, Any]] =
//     val options = SwaggerUIOptions.default.copy(pathPrefix = List("docs", "swagger"))
//     SwaggerUI[zio.RIO[GameFinder, *]](docsAsYaml, options = options)

//   private val redocEndpoint: List[ZServerEndpoint[GameFinder, Any]] =
//     val options = RedocUIOptions.default.copy(pathPrefix = List("docs", "redoc"))
//     Redoc[zio.RIO[GameFinder, *]]("Trading Bot", spec = docsAsYaml, options = options)

//   private val rest: List[ZServerEndpoint[GameFinder, Any]] = controller.rest
//   private val endpoints: List[ZServerEndpoint[GameFinder, Any]] =
//     controller.rest ++ swaggerEndpoint ++ redocEndpoint

//   val app =
//     zioInterpreter.toHttp(endpoints).withDefaultErrorResponse

//   protected lazy val clientLayer = Client.default.orDie

  
//   def run =
//     Server
//       .serve(app)
//       .provide(
//         configLayer,
//         clientLayer,
//         Server.default,
//         BoardValidator.Impl.layer,
//         GameFinder.Impl.layer,
//         Searcher.Impl.layer,
//         GameDownloader.Impl.layer,
//         ChessDotComClient.Impl.layer,
//       )
