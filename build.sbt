import scala.language.postfixOps

val v = new {
  val Scala          = "3.7.2"
  val Munit          = "1.1.1"
  val Testcontainers = "1.21.3"
  val Postgresql = "42.7.6"
  val Flyway = "11.8.2"
  val HikariCP = "6.3.0"
}

ThisBuild / version := "0.0.0-SNAPSHOT"
ThisBuild / scalaVersion := v.Scala
ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-deprecation",
  "-unchecked",
  "-feature",
  "-explaintypes",
  "-experimental",
  "-Xmax-inlines",
  "60"
)

lazy val `q-root` = (project in file("."))
  .settings(publishLocal := {}, publish := {}, publishArtifact := false)
  .aggregate(`q-core`, `q-jdbc`)

lazy val `q-core` = (project in file("q-core"))
  .settings(
    name := "q-core",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % v.Munit % Test withSources,
    )
  )

lazy val `q-jdbc` = (project in file("q-jdbc"))
  .settings(
    name := "q-relational",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % v.Munit % Test withSources,
      "org.testcontainers" % "testcontainers" % v.Testcontainers % Test withSources,
      "org.postgresql" % "postgresql" % v.Postgresql % Test withSources,
      "com.zaxxer" % "HikariCP" % v.HikariCP withSources,
      "org.testcontainers" % "postgresql" % v.Testcontainers % Test withSources,
      "org.flywaydb" % "flyway-database-postgresql" % v.Flyway % Test withSources,
      "org.slf4j" % "slf4j-simple" % "2.0.12" % Test
    )
  ).dependsOn(`q-core`)


