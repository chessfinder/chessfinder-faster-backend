import munit.FunSuite
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.ClientContext
import chessfinder.api.ValidationLambda
import io.circe.parser

class LambdaSpec extends FunSuite {

  test("ValidationLambda should succeed if the board is valid") {

    val inputStr =
      """
        |{
        |"requestId": "123",
        |"board": "????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"
        |}
    """.stripMargin
    val inputStream  = new ByteArrayInputStream(inputStr.getBytes(StandardCharsets.UTF_8))
    val outputStream = new ByteArrayOutputStream()

    val context = new Context {

      override def getLogStreamName(): String = ???

      override def getMemoryLimitInMB(): Int = ???

      override def getRemainingTimeInMillis(): Int = ???

      override def getClientContext(): ClientContext = ???

      override def getInvokedFunctionArn(): String = ???

      override def getFunctionVersion(): String = ???

      override def getIdentity(): CognitoIdentity = ???

      override def getFunctionName(): String = ???

      override def getLogger(): LambdaLogger = ???

      override def getAwsRequestId(): String = ???

      override def getLogGroupName(): String = ???

    }

    ValidationLambda.handleRequest(input = inputStream, output = outputStream, context = context)

    val actualResposneStr  = outputStream.toString(StandardCharsets.UTF_8)
    val actualResponseJson = parser.parse(actualResposneStr).toTry.get

    val expectedResponseStr =
      """
        |{
        |  "requestId" : "123",
        |  "isValid" : true,
        |  "comment" : null
        |}
    """.stripMargin
    val expectedResponseJson = parser.parse(expectedResponseStr).toTry.get

    assertEquals(actualResponseJson, expectedResponseJson)
  }

  test("ValidationLambda should return false if the board is invalid") {

    val inputStr =
      """
        |{
        |"requestId": "123",
        |"board": "not_an_actual_board"
        |}
    """.stripMargin
    val inputStream  = new ByteArrayInputStream(inputStr.getBytes(StandardCharsets.UTF_8))
    val outputStream = new ByteArrayOutputStream()

    val context = new Context {

      override def getLogStreamName(): String = ???

      override def getMemoryLimitInMB(): Int = ???

      override def getRemainingTimeInMillis(): Int = ???

      override def getClientContext(): ClientContext = ???

      override def getInvokedFunctionArn(): String = ???

      override def getFunctionVersion(): String = ???

      override def getIdentity(): CognitoIdentity = ???

      override def getFunctionName(): String = ???

      override def getLogger(): LambdaLogger = ???

      override def getAwsRequestId(): String = ???

      override def getLogGroupName(): String = ???

    }

    ValidationLambda.handleRequest(input = inputStream, output = outputStream, context = context)

    val actualResposneStr  = outputStream.toString(StandardCharsets.UTF_8)
    val actualResponseJson = parser.parse(actualResposneStr).toTry.get

    val expectedResponseStr =
      """
        |{
        |  "requestId" : "123",
        |  "isValid" : false,
        |  "comment" : null
        |}
    """.stripMargin
    val expectedResponseJson = parser.parse(expectedResponseStr).toTry.get

    assertEquals(actualResponseJson, expectedResponseJson)
  }

}
