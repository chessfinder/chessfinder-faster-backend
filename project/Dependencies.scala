import sbt.Keys.libraryDependencies
import sbt.*
import sbt.librarymanagement.ModuleID

object Dependencies {

//   val spireVersion: String      = "0.17.0"
//   val catsVersion: String       = "2.6.1"
//   val taggingVersion: String    = "2.3.1"
//   val enumeratumVersion: String = "1.7.0"
//   val scalatestVersion: String  = "3.2.10"
//   val scalacheckVersion: String = "1.15.4"
//   val scalatestplusVersions: (String, String) =
//     (scalatestVersion + ".0", scalacheckVersion.split('.').take(2).mkString("-"))
//   val scalaMockVersion: String = "5.1.0"
//   val version: String     = "1.1.1"
// //  val tapirAkkaVersion: String = "0.18.3"
//   val akkaHttpVersion: String          = "10.2.7"
//   val akkaVersion: String              = "2.6.17"
//   val logbackVersion: String           = "1.2.7"
//   val binanceVersion: String           = "1.3.8"
//   val circeVersion: String             = "0.14.1"
//   val binanceAkkaClientVersion: String = "0.0.1"
//   val scalaLoggingVersion: String      = "3.9.4"
//   val slickVersion: String             = "3.3.3"
//   val postgresDriverVersion: String    = "42.3.1"
//   val slickPgVersion: String           = "0.20.2"

  // val spire: Seq[ModuleID] = Seq(
  //   "org.typelevel" %% "spire" % spireVersion
  // )

  // val cats: Seq[ModuleID] = Seq(
  //   "org.typelevel" %% "cats-core" % catsVersion
  // )

  // val tagging: Seq[ModuleID] = Seq(
  //   "com.softwaremill.common" %% "tagging" % taggingVersion
  // )

  // val enumeratum: Seq[ModuleID] = Seq(
  //   "com.beachape" %% "enumeratum"       % enumeratumVersion,
  //   "com.beachape" %% "enumeratum-slick" % enumeratumVersion
  // )

  // val scalaTest: Seq[ModuleID] = Seq(
  //   "org.scalatest" %% "scalatest-flatspec"       % scalatestVersion % Test,
  //   "org.scalatest" %% "scalatest-shouldmatchers" % scalatestVersion % Test
  // )

  // val scalaCheck: Seq[ModuleID] = Seq(
  //   // https://mvnrepository.com/artifact/org.scalacheck/scalacheck
  //   "org.scalacheck"    %% "scalacheck"                              % "1.15.4"                 % Test,
  //   "org.scalatestplus" %% s"scalacheck-${scalatestplusVersions._2}" % scalatestplusVersions._1 % "test"
  // )

  lazy val scalaMock: Seq[ModuleID] = {
    val version = "5.1.0"
    Seq(
      "org.scalamock" %% "scalamock" % version
    )
  }

  lazy val wiremock: Seq[ModuleID] = {
    val version = "2.33.2"
    Seq(
      "com.github.tomakehurst" % "wiremock-jre8" % version
    )
  }

  lazy val `zio-http`: Seq[ModuleID] = {
    val version = "0.0.4"
    Seq(
      "dev.zio" %% "zio-http" % version
    )
  }

  lazy val circe: Seq[ModuleID] = {
    val version = "0.14.3"
    Seq(
      "io.circe" %% "circe-core"    % version,
      "io.circe" %% "circe-generic" % version,
      "io.circe" %% "circe-parser"  % version
    )
  }

  lazy val tapir = {
    val version          = "1.2.10"
    val circeYamlVersion = "0.3.2"
    Seq(
      "com.softwaremill.sttp.tapir"   %% "tapir-core"            % version,
      "com.softwaremill.sttp.tapir"   %% "tapir-json-circe"      % version,
      "com.softwaremill.sttp.tapir"   %% "tapir-cats"            % version,
      "com.softwaremill.sttp.tapir"   %% "tapir-zio-http-server" % version,
      "com.softwaremill.sttp.tapir"   %% "tapir-enumeratum"      % version,
      "com.softwaremill.sttp.tapir"   %% "tapir-swagger-ui"      % version,
      "com.softwaremill.sttp.tapir"   %% "tapir-redoc"           % version,
      "com.softwaremill.sttp.tapir"   %% "tapir-openapi-docs"    % version,
      "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml"    % circeYamlVersion
    )
  }

  // lazy val akkaHttp = Seq(
  //   "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
  //   "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
  // )

  lazy val zio = {
    val version = "2.0.2"
    Seq("dev.zio" %% "zio" % version)
  }

  lazy val scalachess = {
    val version = "14.5.5"
    Seq("org.lichess" %% "scalachess" % version)
  }

  lazy val munit = {
    val version = "1.0.0-M7"
    Seq(
      "org.scalameta" %% "munit"            % version,
      "org.scalameta" %% "munit-scalacheck" % version
    )
  }

  // lazy val `zio-munit` = {
  //   val version = "0.1.1"
  //   Seq("com.github.poslegm" %% "munit-zio" % version)
  // }

  lazy val `zio-test` = {
    val version = "2.0.10"
    Seq(
      "dev.zio" %% "zio-test"          % version,
      "dev.zio" %% "zio-test-sbt"      % version,
      "dev.zio" %% "zio-test-magnolia" % version
    )
  }

  lazy val scalaCheck = {
    val version = "1.17.0"
    Seq(
      "org.scalacheck" %% "scalacheck" % version
    )
  }

  lazy val ornicar = {
    val version = "9.1.2"
    Seq("com.github.ornicar" %% "scalalib" % version)
  }

//  lazy val zioPrelude = Seq("dev.zio" %% "zio-prelude" % "0.0.0+1-a56dda2d-SNAPSHOT")

  // https://mvnrepository.com/artifact/dev.zio/zio-dynamodb
//   lazy val `zio-dynamodb` = Seq("dev.zio" %% "zio-dynamodb" % "0.2.0-RC2")

//   lazy val `zio-aws` = Seq("dev.zio" %% "zio-aws-http4s" % "5.17.271.1")

//   lazy val `zio-schema` = Seq(
//     "dev.zio" %% "zio-schema"            % "0.2.0",
//     "dev.zio" %% "zio-schema-derivation" % "0.2.0"
//   )

//   lazy val `zio-slick` = Seq(
//     "io.scalac" %% "zio-slick-interop" % "0.5.0"
//   )

//   lazy val `zio-prelude` = Seq(
//     // https://mvnrepository.com/artifact/dev.zio/zio-prelude
//     "dev.zio" %% "zio-prelude" % "1.0.0-RC15"
//   )

//   lazy val akka = Seq(
//     "com.typesafe.akka" %% "akka-stream"       % akkaVersion,
//     "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
//     "com.typesafe.akka" %% "akka-actor-typed"  % akkaVersion
//   )

//   lazy val ficus = Seq(
//     "com.iheart" %% "ficus" % "1.5.2"
//   )

//   lazy val logging = Seq(
//     "ch.qos.logback"              % "logback-classic" % logbackVersion,
//     "com.typesafe.scala-logging" %% "scala-logging"   % scalaLoggingVersion
//   )

//   lazy val binance = Seq(
//     // https://mvnrepository.com/artifact/io.github.paoloboni/binance-scala-client
// //    "io.github.paoloboni" %% "binance-scala-client" % binanceVersion
//     "plato.eudemonia" %% "binance-akka-client" % binanceAkkaClientVersion
//   )

//   lazy val db: Seq[ModuleID] = Seq(
//     "com.typesafe.slick"  %% "slick"               % slickVersion,
//     "com.typesafe.slick"  %% "slick-hikaricp"      % slickVersion,
//     "org.postgresql"       % "postgresql"          % postgresDriverVersion,
//     "com.github.tminglei" %% "slick-pg"            % slickPgVersion,
//     "com.github.tminglei" %% "slick-pg_circe-json" % slickPgVersion
//   )

  val prod: Seq[ModuleID] = zio ++ tapir ++ scalachess ++ ornicar ++ `zio-http` ++ circe

  val tests: Seq[ModuleID] =
    (munit ++ scalaCheck ++ `zio-test` ++ wiremock).map(_ % Test)
}
