import scala.language.postfixOps

val v = new {
  val Scala = "3.7.2"
  val Munit = "1.1.1"
  val Testcontainers = "1.21.3"
  val Postgresql = "42.7.6"
  val HikariCP = "6.3.0"
  val Cassandra = "4.19.0"
  val Zio = "2.1.21"
}

ThisBuild / version := "0.0.0-SNAPSHOT"
ThisBuild / scalaVersion := v.Scala
ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-Xcheck-macros",
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
  .aggregate(`q-core`, `q-jdbc`, `q-cassandra`, `q-jdbc-zio`)

lazy val `q-core` = (project in file("q-core"))
  .settings(
    name := "q-core",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % v.Munit % Test withSources
    )
  )

lazy val `q-jdbc` = (project in file("q-jdbc"))
  .settings(
    libraryDependencies ++= Seq(
      "com.zaxxer" % "HikariCP" % v.HikariCP withSources,
      "org.scalameta" %% "munit" % v.Munit % Test withSources,
      "org.postgresql" % "postgresql" % v.Postgresql % Test withSources,
      "org.testcontainers" % "postgresql" % v.Testcontainers % Test withSources
    )
  )
  .dependsOn(`q-core`)

lazy val `q-cassandra` = (project in file("q-cassandra"))
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.cassandra" % "java-driver-core" % v.Cassandra withSources,
      "org.scalameta" %% "munit" % v.Munit % Test withSources,
      "org.testcontainers" % "cassandra" % v.Testcontainers % Test withSources
    )
  )
  .dependsOn(`q-core`)

lazy val `q-jdbc-zio` = (project in file("q-jdbc-zio"))
  .settings(
    name := "q-jdbc-zio",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % v.Zio,
    )
  )
  .dependsOn(`q-jdbc`)