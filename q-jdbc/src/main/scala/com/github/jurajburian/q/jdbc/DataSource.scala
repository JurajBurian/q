package com.github.jurajburian.q.jdbc

import com.github.jurajburian.q.*
import java.io.Closeable
import java.sql.{PreparedStatement, ResultSet, Savepoint}

type CloseableIterator[T] = AutoCloseable & Iterator[T]

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
  def read[T](sql: Q)(using deserializer: ResultSet => T): Iterable[T]

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
  def readLazy[T](sql: Q)(using deserializer: ResultSet => T): CloseableIterator[T]

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
  def write[T](sql: Q)(using deserializer: ResultSet => T): Iterable[T]

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
  def writeLazy[T](sql: Q)(using deserializer: ResultSet => T): CloseableIterator[T]

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

/** Represents a data source that can execute SQL statements
  */
trait DataSource extends ReadDataSource with WriteDataSource


/** Represents a data source that can automatically manage transactions for read and write operations.
  */
trait AutoDataSource extends DataSource {

  /** Provides access to a manually managed read only data source.
    *
    * This allows for executing multiple operations within a single transaction context.
    *
    * @return
    * an instance of [[ManuallyManaged & ReadDataSource]] for manual transaction management
    */
  def manuallyManagedReadDataSource: ManuallyManaged & ReadDataSource

  /** Provides access to a manually managed read-write data source.
    *
    * This allows for executing multiple operations within a single transaction context.
    *
    * @return
    *   an instance of [[ManualManagedWriteDataSource]] for manual transaction management
    */
  def manualylManagedWriteDataSource: ManuallyManaged & WriteDataSource
}

trait CloseableDataSource extends AutoDataSource with Closeable
