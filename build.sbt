import sbt.Keys._

lazy val root = (project in file(".")).
  settings(
    name := "GerritWacher",
    version := "1.0",
    scalaVersion := "2.11.4",
    libraryDependencies += "org.rogach" %% "scallop" % "0.9.5",
    libraryDependencies += "com.typesafe.play" % "play-json_2.11" % "2.4.0-M2"
  )
