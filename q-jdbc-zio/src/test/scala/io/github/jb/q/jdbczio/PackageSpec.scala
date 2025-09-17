package io.github.jb.q.jdbczio

import io.github.jb.q.jdbc.HikariDataSource
import io.github.jb.q.*
import zio.test.*
import zio.test.Assertion.*
import zio.{Chunk, ConfigProvider, Runtime, ZIO, ZLayer}

object PackageSpec extends ZIOSpecDefault {
  override def spec = suite("jdbczio package")(
    test("hikariConfig should load HikariConfig from configProvider") {
      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "jdbcUrl" -> "jdbc:postgresql://localhost:5432/test",
          "username" -> "test",
          "password" -> "test",
          "maximumPoolSize" -> "10"
        ),
        "."
      )

      for {
        config <- configProvider.load(hikariConfig)
      } yield {
        assert(config.getJdbcUrl)(equalTo("jdbc:postgresql://localhost:5432/test")) &&
        assert(config.getUsername)(equalTo("test")) &&
        assert(config.getMaximumPoolSize)(equalTo(10))
      }
    },
    test("loadHikariConfig should load HikariConfig from runtime configProvider") {
      for {
        config <- loadHikariConfig(Chunk("database"))
      } yield {
        assert(config.getJdbcUrl)(equalTo("jdbc:postgresql://localhost:5432/test")) &&
        assert(config.getUsername)(equalTo("test")) &&
        assert(config.getMaximumPoolSize)(equalTo(20))
      }
    }.provideSomeLayer {
      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "database.jdbcUrl" -> "jdbc:postgresql://localhost:5432/test",
          "database.username" -> "test",
          "database.password" -> "test",
          "database.maximumPoolSize" -> "20"
        ),
        "."
      )
      Runtime.setConfigProvider(configProvider)
    },
    test("dataSource should provide HikariDataSource") {
      for {
        ds <- ZIO.service[HikariDataSource]
        res <- ZIO.attemptBlocking(ds.read(q"select 1"))
      } yield assertTrue(res.nonEmpty)
    }.provideSomeLayer {
      val configProvider: ConfigProvider = ConfigProvider.fromMap(
        Map(
          "jdbcUrl" -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
          "username" -> "sa",
          "password" -> ""
        ),
        "."
      )
      Runtime.setConfigProvider(configProvider) >>> dataSource()
    }
  )
}
