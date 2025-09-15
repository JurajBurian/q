package io.github.jb.q.cassandra

import com.datastax.oss.driver.api.core.cql.{ResultSet, Row}
import io.github.jb.q.*

import java.io.Closeable
import scala.concurrent.Future

trait Closed {
  def isClosed: Boolean
}

type CloseableIterator[T] = Closeable & Closed & Iterator[T]

/** Represents a data source that can execute read-only SQL statements
  */
trait DataSource {

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

  def apply[T](sql: Q)(using deserializer: Row => T): Iterable[T]

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
  def manual[T](sql: Q)(using deserializer: Row => T): CloseableIterator[T]

  // TODO ....
  // def async[T](sql: Q)(using deserializer: Row => T): Future[Iterable[T]]
  // def asyncManual[T](sql: Q)(using deserializer: Row => T): Future[CloseableIterator[T]]

}
