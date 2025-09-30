package io.github.jb.q

import io.github.jb.q.jdbc.HikariDataSource
import com.zaxxer.hikari.HikariConfig
import zio.{Chunk, Config, Scope, ZIO, ZLayer}

import java.util.Properties
import scala.jdk.CollectionConverters.MapHasAsJava

package object jdbczio {

  val hikariConfig: Config[HikariConfig] = Config.table(Config.string).mapAttempt { cfg =>
    val props = new Properties()
    props.putAll(cfg.asJava)
    new HikariConfig(props)
  }

  def loadHikariConfig(configPath: Chunk[String] = Chunk.empty): ZIO[Any, Config.Error, HikariConfig] =
    ZIO.config {
      if (configPath.isEmpty) hikariConfig else hikariConfig.nested(configPath.head, configPath.tail*)
    }

  def makeDataSource(config: HikariConfig): ZIO[Scope, Nothing, HikariDataSource] =
    ZIO.fromAutoCloseable(ZIO.succeed(HikariDataSource.apply(config)))

  def dataSource(configPath: Chunk[String] = Chunk.empty): ZLayer[Any, Throwable, HikariDataSource] =
    ZLayer.scoped(loadHikariConfig(configPath).flatMap(makeDataSource))

}
