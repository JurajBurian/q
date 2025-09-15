package io.github.jb.q.cassandra

import com.datastax.oss.driver.api.core.cql.{BoundStatement, PreparedStatement, ResultSet, Row}
import com.datastax.oss.driver.api.core.CqlSession
import io.github.jb.q.*

import java.io.Closeable

/** Function to bind parameters to prepared statement
  */
type StatementBinder = (Q, PreparedStatement) => BoundStatement

/** A data source that uses a provided JDBC connection for executing SQL statements.
  *
  * This class allows executing read operations using the given connection. It supports both eager and lazy result
  * retrieval, as well as parameter binding for prepared statements.
  *
  * @param session
  *   the JDBC connection to use for executing SQL statements
  * @param autoClose
  *   if true, the connection will be closed automatically after each operation; if false, the caller is responsible for
  *   closing it
  */
private[cassandra] case class ManuallyReadDatasourceWithConnection(
    session: CqlSession,
    bindStatement: StatementBinder,
    autoClose: Boolean
) extends DataSource
    with Closeable {

  override def apply[T](sql: Q)(using deserializer: Row => T): Iterable[T] = try {
    val statement = session.prepare(sql.query)
    executeTypedEager(bindStatement(sql, statement))
  } finally {
    if (autoClose) close()
  }

  override def manual[T](sql: Q)(using deserializer: Row => T): CloseableIterator[T] = {
    val statement = session.prepare(sql.query)
    bindStatement(sql, statement)
    executeTypedLazy(bindStatement(sql, statement), autoClose)
  }

  override def close(): Unit = session.close()

  protected def executeTypedEager[T](statement: BoundStatement)(using deserializer: Row => T): Vector[T] = {
    val resultSet = session.execute(statement)
    val it = resultSet.iterator()
    val builder = Vector.newBuilder[T]
    while (it.hasNext) {
      builder += deserializer(it.next())
    }
    builder.result()
  }

  protected def executeTypedLazy[T](statement: BoundStatement, autoClose: Boolean)(using
      deserializer: Row => T
  ): CloseableIterator[T] = {
    new Iterator[T] with Closeable with Closed {
      private val resultSet = session.execute(statement)
      private val it = resultSet.iterator()
      override def isClosed: Boolean = session.isClosed
      override def hasNext: Boolean = it.hasNext

      override def next(): T = {
        if (!hasNext) throw new NoSuchElementException()
        val result = deserializer(it.next())
        result
      }

      override def close(): Unit = if (autoClose) session.close()

    }
  }
}
