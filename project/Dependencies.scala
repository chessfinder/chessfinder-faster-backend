import sbt.Keys.libraryDependencies
import sbt.*
import sbt.librarymanagement.ModuleID

object Dependencies {

  lazy val `chessfinder-core-tests`: Seq[ModuleID] = (munit ++ scalatest ++ scalaCheck).map(_ % Test)

  lazy val circe: Seq[ModuleID] = {
    val version = "0.14.3"
    Seq(
      "io.circe" %% "circe-core"    % version,
      "io.circe" %% "circe-generic" % version,
      "io.circe" %% "circe-parser"  % version
    )
  }

  lazy val scalachess = {
    val version = "14.5.5"
    // Seq("org.lichess" %% "scalachess" % version exclude ("com.github.ornicar", "*"))
    Seq("org.lichess" %% "scalachess" % version)
  }

  lazy val munit = {
    val version = "1.0.0-M7"
    Seq(
      "org.scalameta" %% "munit"            % version,
      "org.scalameta" %% "munit-scalacheck" % version
    )
  }

  lazy val scalatest = {
    val version = "3.2.15"
    Seq(
      "org.scalatest" %% "scalatest" % version
    )
  }

  lazy val scalaCheck = {
    val version = "1.17.0"
    Seq(
      "org.scalacheck" %% "scalacheck" % version
    )
  }

  lazy val `java-aws` = {
    Seq(
      "com.amazonaws" % "aws-lambda-java-events" % "3.11.1",
      "com.amazonaws" % "aws-lambda-java-core"   % "1.2.2"
    )
  }

  val prod: Seq[ModuleID] = circe ++ `java-aws`

  val tests: Seq[ModuleID] =
    (munit ++ scalaCheck).map(_ % Test)
}
