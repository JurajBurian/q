package com.github.jurajburian.q.jdbc

import com.github.jurajburian.q.*
import com.zaxxer.hikari

import java.sql.ResultSet

case class HikariDataSource(private val hikariDataSource: hikari.HikariDataSource) extends CloseableDataSource {

  val read: ReadDataSource & ReadUnmanaged = new ReadDataSource with ReadUnmanaged {

    /** Executes a read-only SQL statement and returns typed results
      *
      * @param sql
      *   the Sql to execute
      * @param deserializer
      *   function to convert a ResultSet to a typed result
      * @tparam T
      *   type of the result
      * @return
      *   iterable of typed results
      */
    override def apply[T](sql: Q)(using deserializer: ResultSet => T): Iterable[T] =
      ManuallyReadDatasourceWithConnection(hikariDataSource.getConnection, true).apply(sql)

    /** Executes a read-only SQL statement and returns typed results in closeable iterator
      *
      * @param sql
      *   the Sql to execute
      * @param deserializer
      *   function to convert a ResultSet to a typed result
      * @tparam T
      *   type of the result
      * @return
      *   iterator of typed results, if not fully consumed, it must be closed to release resources
      */
    override def manual[T](sql: Q)(using deserializer: ResultSet => T): CloseableIterator[T] =
      ManuallyReadDatasourceWithConnection(hikariDataSource.getConnection, true).manual(sql)

    override def unmanaged: ReadDataSource & ManuallyManaged =
      ManuallyReadDatasourceWithConnection(hikariDataSource.getConnection, false)
  }

  val write: WriteDataSource & WriteUnmanaged = new WriteDataSource with WriteUnmanaged {

    /** Executes a read-write SQL statement and returns typed results
      *
      * @param sql
      *   the Sql to execute
      * @param deserializer
      *   function to convert a ResultSet to a typed result
      * @tparam T
      *   type of the result
      * @return
      *   iterable of typed results
      */
    override def apply[T](sql: Q)(using deserializer: ResultSet => T): Iterable[T] =
      ManuallyWriteDatasourceWithConnection(hikariDataSource.getConnection, true)(sql)

    /** Executes a read-write SQL statement and returns typed results
      *
      * @param sql
      *   the Sql to execute
      * @param deserializer
      *   function to convert a ResultSet to a typed result
      * @tparam T
      *   type of the result
      * @return
      *   iterator of typed results, if not fully consumed, it must be closed to release resources
      */
    override def manual[T](sql: Q)(using deserializer: ResultSet => T): CloseableIterator[T] =
      ManuallyWriteDatasourceWithConnection(hikariDataSource.getConnection, true).manual(sql)

    /** Executes an update SQL statement
      *
      * @param sql
      *   to execute
      * @return
      *   number of affected rows
      */
    override def update(sql: Q): Int =
      ManuallyWriteDatasourceWithConnection(hikariDataSource.getConnection, true).update(sql)

    override def unmanaged: WriteDataSource & ManuallyManaged =
      ManuallyWriteDatasourceWithConnection(hikariDataSource.getConnection, false)
  }

  override def close(): Unit = hikariDataSource.close()

}

object HikariDataSource {
  def apply(config: hikari.HikariConfig): HikariDataSource = {
    new HikariDataSource(new hikari.HikariDataSource(config))
  }
}
