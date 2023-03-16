import sbt.*
import EndToEndSettings._
import sbt.{ Def, _ }
import sbt.Keys._

ThisBuild / organization      := "unknown"
ThisBuild / version           := "0.1"
ThisBuild / scalaVersion      := "3.2.2"
ThisBuild / semanticdbEnabled := true // enable SemanticDB
ThisBuild / testFrameworks ++= List(
  new TestFramework("munit.Framework"),
  new TestFramework("zio.test.sbt.ZTestFramework")
)
// ThisBuild / licenses += "AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")

val lilaMaven = "lila-maven" at "https://raw.githubusercontent.com/lichess-org/lila-maven/master"
val sonashots = "sonashots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val DeepIntegrationTest = IntegrationTest.extend(Test)

lazy val DeepIntegrationSettings: Seq[Def.Setting[_]] =
  inConfig(DeepIntegrationTest)(Defaults.testSettings) ++
    Seq(
      DeepIntegrationTest / fork              := false,
      DeepIntegrationTest / parallelExecution := false,
      DeepIntegrationTest / scalaSource       := baseDirectory.value / "src/it/scala"
    )

lazy val root = (project in file("."))
  .configs(DeepIntegrationTest)
  .settings(DeepIntegrationSettings)
  .settings(
    name := "chess-finder",
    libraryDependencies ++= Dependencies.prod ++ Dependencies.tests,
    scalacOptions := Seq(
      "-encoding",
      "utf-8",
      // "-rewrite",
      "-source:future-migration",
      "-indent",
      "-explaintypes",
      "-feature",
      "-language:postfixOps",
      "-deprecation",
      "-Ykind-projector"
      // "-Xfatal-warnings" // Warnings as errors!
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
