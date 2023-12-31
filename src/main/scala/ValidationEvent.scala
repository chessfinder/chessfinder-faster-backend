package chessfinder

import io.circe.{ Codec, Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveCodec, deriveDecoder, deriveEncoder }

case class ValidationCommand(
    requestId: String,
    board: String
)

object ValidationCommand:
  given Decoder[ValidationCommand] = deriveDecoder[ValidationCommand]

case class ValidationResult(
    requestId: String,
    isValid: Boolean,
    comment: Option[String]
)

object ValidationResult:
  given Encoder[ValidationResult] = deriveEncoder[ValidationResult]
