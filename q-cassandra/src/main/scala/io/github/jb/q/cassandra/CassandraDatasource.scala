package io.github.jb.q.cassandra

import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.{CqlSession, CqlSessionBuilder}
import io.github.jb.q.Q

import java.io.Closeable
import scala.annotation.targetName

case class CassandraDatasource(private val builder: CqlSessionBuilder, binder: StatementBinder) extends DataSource {

  /** Executes a read/write statement and returns typed results in closeable iterator
    *
    * @param sql
    *   the Sql to execute
    * @param deserializer
    *   function to convert a Row to a typed result
    * @tparam T
    *   type of the result
    * @return
    *   iterator of typed results, if not fully consumed, it must be closed to release resources
    */
  override def manual[T](sql: Q)(using deserializer: Row => T): CloseableIterator[T] =
    ManuallyReadDatasourceWithConnection(builder.build(), binder, true).manual(sql)

  /** Executes a read/write statement and returns typed results
    *
    * @param sql
    *   the Sql to execute
    * @param deserializer
    *   function to convert a Row to a typed result
    * @tparam T
    *   type of the result
    * @return
    *   iterable of typed results
    */
  override def apply[T](sql: Q)(using deserializer: Row => T): Iterable[T] =
    ManuallyReadDatasourceWithConnection(builder.build(), binder, true)(sql)

  def unmanaged: DataSource & Closeable = ManuallyReadDatasourceWithConnection(builder.build(), binder, false)
}

object CassandraDatasource {

  val defaultStatementBinder: StatementBinder = { (sql, statement) =>
    statement.bind(sql.bindings*)
  }

  @targetName("applyWithUsing")
  def apply(builder: CqlSessionBuilder)(using binder: StatementBinder = defaultStatementBinder): CassandraDatasource =
    CassandraDatasource(builder, binder)

}
