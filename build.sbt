ThisBuild / organization      := "unknown"
ThisBuild / version           := "0.1"
ThisBuild / scalaVersion      := "3.2.2"
ThisBuild / semanticdbEnabled := true // enable SemanticDB
// ThisBuild / licenses += "AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")

val lilaMaven = "lila-maven" at "https://raw.githubusercontent.com/lichess-org/lila-maven/master"
val sonashots = "sonashots" at "https://oss.sonatype.org/content/repositories/snapshots"

val root = (project in file("."))
  .settings(
    name := "chess-finder",
    libraryDependencies ++= Dependencies.prod ++ Dependencies.tests,
    testFrameworks ++= List(
      new TestFramework("weaver.framework.CatsEffect"),
      new TestFramework("munit.Framework")
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
