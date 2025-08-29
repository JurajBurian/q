package com.github.jurajburian.q.jdbc

import com.github.jurajburian.q.*

import java.sql.{Connection, PreparedStatement, ResultSet}

private[jdbc] trait ManuallyManagedImpl(connection: Connection) extends ManuallyManaged {

  self =>

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

  /** Bind the parameters of the query to the prepared statement.
    *
    * @param q
    *   the query containing the SQL statement and bindings
    * @param statement
    *   the prepared statement to bind the parameters to
    */
  protected def bindStatement(q: Q, statement: PreparedStatement): Unit = {
    var idx = 1
    @inline
    def index() = {
      val ret = idx
      idx = idx + 1
      ret
    }

    q.bindings.foreach {
      case null => statement.setNull(index(), java.sql.Types.NULL)

      case v: SqlBindable => v.bind(statement, index())

      // String types
      case v: String => statement.setString(index(), v)
      case v: Char   => statement.setString(index(), v.toString)

      // Numeric types
      case v: Boolean              => statement.setBoolean(index(), v)
      case v: Int                  => statement.setInt(index(), v)
      case v: Long                 => statement.setLong(index(), v)
      case v: Float                => statement.setFloat(index(), v)
      case v: Double               => statement.setDouble(index(), v)
      case v: Short                => statement.setShort(index(), v)
      case v: Byte                 => statement.setByte(index(), v)
      case v: java.math.BigDecimal => statement.setBigDecimal(index(), v)
      case v: BigDecimal           => statement.setBigDecimal(index(), v.bigDecimal)

      // Date and time types
      case v: java.sql.Time           => statement.setTime(index(), v)
      case v: java.sql.Timestamp      => statement.setTimestamp(index(), v)
      case v: java.sql.Date           => statement.setDate(index(), v)
      case v: java.util.Date          => statement.setTimestamp(index(), new java.sql.Timestamp(v.getTime))
      case v: java.time.LocalDate     => statement.setDate(index(), java.sql.Date.valueOf(v))
      case v: java.time.LocalTime     => statement.setTime(index(), java.sql.Time.valueOf(v))
      case v: java.time.LocalDateTime => statement.setTimestamp(index(), java.sql.Timestamp.valueOf(v))
      case v: java.time.Instant       => statement.setTimestamp(index(), java.sql.Timestamp.from(v))

      // Binary data
      case v: Array[Byte]         => statement.setBytes(index(), v)
      case v: java.sql.Blob       => statement.setBlob(index(), v)
      case v: java.io.InputStream => statement.setBinaryStream(index(), v)

      // Character streams
      case v: java.io.Reader => statement.setCharacterStream(index(), v)

      // Other SQL types
      case v: java.sql.Array => statement.setArray(index(), v)
      case v: java.sql.Ref   => statement.setRef(index(), v)
      case v: java.net.URL   => statement.setURL(index(), v)

      // Scala specific types
      case v: Option[_] =>
        v match {
          case Some(value) =>
            // Recursively handle the wrapped value
            value match {
              case null => statement.setNull(index(), java.sql.Types.NULL)
              case _    => // Will be handled by the next iteration of pattern matching
            }
          case None => statement.setNull(index(), java.sql.Types.NULL)
        }
      // Fallback for AnyRef types
      case v: AnyRef => statement.setObject(index(), v)

      // Final fallback - convert to string representation
      case v: Any => statement.setString(index(), v.toString)
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
private[jdbc] case class ManuallyReadDatasourceWithConnection(connection: Connection, autoClose: Boolean)
    extends ManuallyManagedImpl(connection)
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

private[jdbc] case class ManuallyWriteDatasourceWithConnection(connection: Connection, autoClose: Boolean)
    extends ManuallyManagedImpl(connection)
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
