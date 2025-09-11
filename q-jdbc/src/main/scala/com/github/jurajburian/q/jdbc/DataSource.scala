package com.github.jurajburian.q.jdbc

import com.github.jurajburian.q.*
import java.io.Closeable
import java.sql.{PreparedStatement, ResultSet, Savepoint}

trait Closed {
  def isClosed: Boolean
}

type CloseableIterator[T] = Closeable & Closed & Iterator[T]

/** Represents a data source that can execute read-only SQL statements
  */
trait ReadDataSource {

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
  def apply[T](sql: Q)(using deserializer: ResultSet => T): Iterable[T]

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
  def manual[T](sql: Q)(using deserializer: ResultSet => T): CloseableIterator[T]

}

/** Represents a data source that can execute read-write SQL statements and update statements
  */
trait WriteDataSource {

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
  def apply[T](sql: Q)(using deserializer: ResultSet => T): Iterable[T]

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
  def manual[T](sql: Q)(using deserializer: ResultSet => T): CloseableIterator[T]

  /** Executes an update SQL statement
    * @param sql
    *   to execute
    * @return
    *   number of affected rows
    */
  def update(sql: Q): Int
}

trait ManuallyManaged {

  /** roll back the transaction
    */
  def rollback(): Unit

  /** set save point
    * @param savePoint
    *   \- name of the save point
    * @return
    *   \- save point object
    */
  def setSavePoint(savePoint: String): Savepoint

  /** rollback to the save point
    * @param savePoint
    *   \- name of the save point
    */
  def rollbackToSavePoint(savePoint: Savepoint): Unit

  /** commit the transaction and close the DataSource (underlying connection)
    */
  def close(): Unit
}

/** Provides access to unmanaged read and write data sources that require manual transaction management.
  */
trait WriteUnmanaged {
  def unmanaged: WriteDataSource & ManuallyManaged
}

/** Provides access to unmanaged read data sources that require manual transaction management.
  */
trait ReadUnmanaged {
  def unmanaged: ReadDataSource & ManuallyManaged
}

/** Represents a data source that can execute both read-only and read-write SQL statements as well as update statements.
  */
trait CloseableDataSource extends Closeable {
  val read: ReadDataSource & ReadUnmanaged
  val write: WriteDataSource & WriteUnmanaged
}
