import sbt.*
import EndToEndSettings._

ThisBuild / organization      := "unknown"
ThisBuild / version           := "0.1"
ThisBuild / scalaVersion      := "3.2.2"
ThisBuild / semanticdbEnabled := true // enable SemanticDB
// ThisBuild / licenses += "AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")

val lilaMaven = "lila-maven" at "https://raw.githubusercontent.com/lichess-org/lila-maven/master"
val sonashots = "sonashots" at "https://oss.sonatype.org/content/repositories/snapshots"

// val IntegrationTests = IntegrationTest.extend(Test)


lazy val root = (project in file("."))
  // .configs(IntegrationTests)
  // .configs(EndToEndTest)
  .settings(
    name := "chess-finder",
    libraryDependencies ++= Dependencies.prod ++ Dependencies.tests,
    testFrameworks ++= List(
      new TestFramework("munit.Framework"),
      new TestFramework("zio.test.sbt.ZTestFramework")
    ),
    scalacOptions := Seq(
      "-encoding",
      "utf-8",
      // "-rewrite",
      "-source:future-migration",
      "-indent",
      "-explaintypes",
      "-feature",
      "-language:postfixOps",
      "-Xfatal-warnings", // Warnings as errors!
      // "-Ywarn-unused-import"
      // "-Wunused:imports",
      // "-Ywarn-unused"
    ),
    resolvers ++= Seq(lilaMaven, sonashots)
  )
  // .settings(EndToEndSettings.e2eSettings)
  .dependsOn(testkit % Test)
  .aggregate(testkit)

lazy val testkit = project
  .in(file("src_testkit"))