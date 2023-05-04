import sbt.Keys.libraryDependencies
import sbt.*
import sbt.librarymanagement.ModuleID

object Dependencies {

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

  lazy val `zio-mock` = {
    val version = "1.0.0-RC9"
    Seq(
      "dev.zio" %% "zio-mock" % version
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

  lazy val `circe-config`: Seq[ModuleID] = {
    val version = "0.10.0"
    Seq(
      "io.circe" %% "circe-config" % version
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
      "com.softwaremill.sttp.tapir"   %% "tapir-aws-lambda"      % version,
      "com.softwaremill.sttp.tapir"   %% "tapir-aws-cdk"         % version,
      "com.softwaremill.sttp.tapir"   %% "tapir-aws-sam"         % version,
      "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml"    % circeYamlVersion
    )
  }

  lazy val tagging = {
    val version = "2.3.4"
    Seq("com.softwaremill.common" %% "tagging" % version)
  }

  lazy val `typesafe-config` = {
    val version = "1.4.2"
    Seq("com.typesafe" % "config" % version)
  }

  lazy val zio = {
    val version = "2.0.2"
    Seq("dev.zio" %% "zio" % version)
  }

  lazy val `zio-lambda` = {
    val version = "1.0.2"
    Seq(
      "dev.zio" %% "zio-lambda"          % version,
      "dev.zio" %% "zio-lambda-response" % version,
      "dev.zio" %% "zio-lambda-event"    % version
    )
  }

  lazy val `zio-config` = {
    val version = "4.0.0-RC14"
    Seq(
      "dev.zio" %% "zio-config"          % version,
      "dev.zio" %% "zio-config-typesafe" % version,
      "dev.zio" %% "zio-config-magnolia" % version
    )
  }

  lazy val `zio-cats` = {
    val version = "3.1.1.0"
    Seq("dev.zio" %% "zio-interop-cats" % version)
  }

  lazy val scalachess = {
    val version = "14.5.5"
    Seq("org.lichess" %% "scalachess" % version exclude ("com.github.ornicar", "*"))
  }

  lazy val munit = {
    val version = "1.0.0-M7"
    Seq(
      "org.scalameta" %% "munit"            % version,
      "org.scalameta" %% "munit-scalacheck" % version
    )
  }

  lazy val `zio-munit` = {
    val version = "0.1.1"
    Seq(
      "com.github.poslegm" % "munit-zio_3" % version
    )
  }

  lazy val `zio-test` = {
    val version = "2.0.10"
    Seq(
      "dev.zio" %% "zio-test"          % version,
      "dev.zio" %% "zio-test-sbt"      % version,
      "dev.zio" %% "zio-test-magnolia" % version
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

  lazy val `zio-logging` = {
    val zioVersion      = "2.1.11"
    val slf4jVersion    = "2.0.7"
    val logbackVersion  = "1.2.10"
    val logstashVersion = "7.3"
    Seq(
      "dev.zio"             %% "zio-logging"              % zioVersion,
      "dev.zio"             %% "zio-logging-slf4j2"       % zioVersion,
      "ch.qos.logback"       % "logback-core"             % logbackVersion,
      "ch.qos.logback"       % "logback-classic"          % logbackVersion,
      "org.slf4j"            % "slf4j-api"                % slf4jVersion,
      "net.logstash.logback" % "logstash-logback-encoder" % logstashVersion
    )
  }

  lazy val `zio-dynamodb` = {
    val version = "0.2.9"
    Seq("dev.zio" %% "zio-dynamodb" % version)
  }

  lazy val `zio-aws` = {
    val version = "6.20.42.1"
    Seq("dev.zio" %% "zio-aws-netty" % version)
  }

  lazy val `zio-schema` = {
    val version = "0.4.10"
    Seq(
      "dev.zio" %% "zio-schema"            % version,
      "dev.zio" %% "zio-schema-derivation" % version
    )
  }

  // lazy val ornicar = {
  //   val version = "9.1.2"
  //   Seq("com.github.ornicar" %% "scalalib" % version)
  // }

  val prod: Seq[ModuleID] =
    zio ++
      tapir ++
      scalachess ++
      `zio-http` ++
      circe ++
      // `circe-config` ++
      // `typesafe-config` ++
      `zio-config` ++
      `zio-lambda` ++
      `zio-logging` ++
      `zio-schema` ++
      `zio-aws` ++
      `zio-dynamodb`

  val tests: Seq[ModuleID] =
    (munit ++ scalaCheck ++ `zio-test` ++ wiremock ++ `zio-mock` ++ `zio-munit`).map(_ % Test)
}
