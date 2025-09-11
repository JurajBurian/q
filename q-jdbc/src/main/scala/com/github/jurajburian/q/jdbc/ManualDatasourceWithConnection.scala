package com.github.jurajburian.q.jdbc

import com.github.jurajburian.q.*

import java.sql.{Connection, PreparedStatement, ResultSet}

/** Function to bind parameters to prepared statement
  */
type StatementBinder = (Q, PreparedStatement) => Unit

private[jdbc] trait ManuallyManagedImpl(connection: Connection) extends ManuallyManaged {

  self =>

  val bindStatement: StatementBinder

  protected inline def setProperties(readOnly: Boolean): Unit = {
    if (connection.isReadOnly != readOnly)
      connection.setReadOnly(readOnly)
    // we do manual commit always
    if (connection.getAutoCommit)
      connection.setAutoCommit(false)
  }

  override def rollback(): Unit = connection.rollback()

  override def setSavePoint(savePoint: String): java.sql.Savepoint = connection.setSavepoint(savePoint)

  override def rollbackToSavePoint(savePoint: java.sql.Savepoint): Unit = connection.rollback(savePoint)

  override def close(): Unit =
    try {
      connection.commit()
    } finally {
      connection.close()
    }

  protected def executeTypedEager[T](statement: PreparedStatement)(using deserializer: ResultSet => T): Vector[T] =
    try {
      val resultSet = statement.executeQuery()
      try {
        val builder = Vector.newBuilder[T]
        while (resultSet.next()) {
          builder += deserializer(resultSet)
        }
        builder.result()
      } finally {
        resultSet.close()
      }
    } finally {
      statement.close()
    }

  protected def executeTypedLazy[T](statement: PreparedStatement, autoClose: Boolean)(using
      deserializer: ResultSet => T
  ): CloseableIterator[T] = {
    new Iterator[T] with AutoCloseable {
      private val resultSet = statement.executeQuery()
      private var hasNextResult: Boolean = resultSet.next()

      override def hasNext: Boolean = {
        hasNextResult
      }

      override def next(): T = {
        if (!hasNextResult) throw new NoSuchElementException()
        val result = deserializer(resultSet)
        hasNextResult = resultSet.next()
        if (!hasNextResult) close()
        result
      }

      override def close(): Unit = try {
        try {
          if (!resultSet.isClosed) resultSet.close()
        } finally {
          statement.close()
        }
      } finally {
        if (autoClose) self.close()
      }
    }
  }
}

/** A data source that uses a provided JDBC connection for executing SQL statements.
  *
  * This class allows executing read operations using the given connection. It supports both eager and lazy result
  * retrieval, as well as parameter binding for prepared statements.
  *
  * @param connection
  *   the JDBC connection to use for executing SQL statements
  * @param autoClose
  *   if true, the connection will be closed automatically after each operation; if false, the caller is responsible for
  *   closing it
  */
private[jdbc] case class ManuallyReadDatasourceWithConnection(
    connection: Connection,
    bindStatement: StatementBinder,
    autoClose: Boolean
) extends ManuallyManagedImpl(connection)
    with ReadDataSource {

  override def apply[T](sql: Q)(using deserializer: ResultSet => T): Iterable[T] = try {
    setProperties(readOnly = true)
    val statement = connection.prepareStatement(sql.query)
    bindStatement(sql, statement)
    executeTypedEager(statement)
  } finally {
    if (autoClose) close()
  }

  override def manual[T](sql: Q)(using deserializer: ResultSet => T): CloseableIterator[T] = {
    setProperties(readOnly = true)
    val statement = connection.prepareStatement(sql.query)
    bindStatement(sql, statement)
    executeTypedLazy(statement, autoClose)
  }

}

private[jdbc] case class ManuallyWriteDatasourceWithConnection(
    connection: Connection,
    bindStatement: StatementBinder,
    autoClose: Boolean
) extends ManuallyManagedImpl(connection)
    with WriteDataSource {

  override def apply[T](sql: Q)(using deserializer: ResultSet => T): Iterable[T] = try {
    setProperties(readOnly = false)
    val statement = connection.prepareStatement(sql.query)
    bindStatement(sql, statement)
    executeTypedEager(statement)
  } finally {
    if (autoClose) close()
  }

  override def manual[T](sql: Q)(using deserializer: ResultSet => T): CloseableIterator[T] = {
    setProperties(readOnly = false)
    val statement = connection.prepareStatement(sql.query)
    bindStatement(sql, statement)
    executeTypedLazy(statement, autoClose)
  }

  override def update(sql: Q): Int = {
    setProperties(readOnly = false)
    val statement = connection.prepareStatement(sql.query)
    bindStatement(sql, statement)
    try {
      statement.executeUpdate()
    } finally {
      if (autoClose) close()
    }
  }
}
