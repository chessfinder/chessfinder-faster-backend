import Dependencies.*

lazy val `tapir-aws-sam` = project
  .in(file("."))
  .settings(
    libraryDependencies ++= circe ++ tapirPartial ++ scalatest.map(_ % Test),
    scalacOptions := Seq("-Ykind-projector")
  )
