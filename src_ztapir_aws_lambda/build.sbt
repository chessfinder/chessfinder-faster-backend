import Dependencies.*

lazy val `ztapir-aws-lambda` = project
  .in(file("."))
  .settings(
    libraryDependencies ++= zio ++ circe ++ tapir ++ (`zio-test` ++ `zio-mock`).map(_ % Test),
    scalacOptions := Seq("-Ykind-projector")
  )
