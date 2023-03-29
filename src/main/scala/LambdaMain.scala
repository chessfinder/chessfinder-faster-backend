// package chessfinder

// import zio.ZIOApp
// import zio.ZIOAppDefault

// import sttp.tapir.ztapir.*
// import sttp.tapir.server.ziohttp.ZioHttpInterpreter
// import zio.http.{ HttpApp, Request, Response }
// import zio.*
// import zio.http.*
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

// import sttp.model.Uri.UriContext
// import zio.Console.*
// import zio.*
// import zio.json.*
// import zio.lambda.*
// import chessfinder.api.FindRequest
// import chessfinder.api.FindResponse

// object LambdaMain extends ZLambda[CustomEvent, String]:

//   override def apply(event: CustomEvent, context: Context): Task[String] =
//     for {
//       _ <- printLine("We are here")
//       _ <- printLine(event.message)
//     } yield "Handler ran successfully"

// final case class CustomEvent(message: String)

// object CustomEvent {
//   implicit val decoder: JsonDecoder[CustomEvent] = DeriveJsonDecoder.gen[CustomEvent]
// }

// final case class CustomResponse(message: String)

// object CustomResponse {
//   implicit val encoder: JsonEncoder[CustomResponse] = DeriveJsonEncoder.gen[CustomResponse]
// }
