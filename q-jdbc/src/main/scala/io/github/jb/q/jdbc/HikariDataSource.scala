package io.github.jb.q.jdbc

import io.github.jb.q.*
import com.zaxxer.hikari

import java.sql.{PreparedStatement, ResultSet}

/** DataSource implementation using HikariCP
  * @param hikariDataSource
  *   instance of datasource
  * @param binder
  *   function to bind parameters to prepared statement
  */
case class HikariDataSource(private val hikariDataSource: hikari.HikariDataSource, binder: StatementBinder)
    extends CloseableDataSource {

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
      ManuallyReadDatasourceWithConnection(hikariDataSource.getConnection, binder, true)(sql)

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
      ManuallyReadDatasourceWithConnection(hikariDataSource.getConnection, binder, true).manual(sql)

    override def unmanaged: ReadDataSource & ManuallyManaged =
      ManuallyReadDatasourceWithConnection(hikariDataSource.getConnection, binder, false)
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
      ManuallyWriteDatasourceWithConnection(hikariDataSource.getConnection, binder, true)(sql)

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
      ManuallyWriteDatasourceWithConnection(hikariDataSource.getConnection, binder, true).manual(sql)

    /** Executes an update SQL statement
      *
      * @param sql
      *   to execute
      * @return
      *   number of affected rows
      */
    override def update(sql: Q): Int =
      ManuallyWriteDatasourceWithConnection(hikariDataSource.getConnection, binder, true).update(sql)

    override def unmanaged: WriteDataSource & ManuallyManaged =
      ManuallyWriteDatasourceWithConnection(hikariDataSource.getConnection, binder, false)
  }

  override def close(): Unit = hikariDataSource.close()

}

object HikariDataSource {

  val defaultStatementBinder: StatementBinder = { (q: Q, statement: PreparedStatement) =>
    var idx = 1
    def index() = {
      val ret = idx
      idx = idx + 1
      ret
    }

    q.bindings.foreach {
      case null => statement.setNull(index(), java.sql.Types.NULL)

      // String types
      case v: String => statement.setString(index(), v)
      case v: Char   => statement.setString(index(), v.toString)

      // Numeric types
      case v: Boolean =>
        statement.setBoolean(index(), v)
      case v: Int =>
        statement.setInt(index(), v)
      case v: Long =>
        statement.setLong(index(), v)
      case v: Float =>
        statement.setFloat(index(), v)
      case v: Double =>
        println("set: " + v)
        statement.setDouble(index(), v)
      case v: Short =>
        statement.setShort(index(), v)
      case v: Byte =>
        statement.setByte(index(), v)
      case v: java.math.BigDecimal =>
        statement.setBigDecimal(index(), v)
      case v: BigDecimal =>
        statement.setBigDecimal(index(), v.bigDecimal)

      // Date and time types
      case v: java.sql.Time =>
        statement.setTime(index(), v)
      case v: java.sql.Timestamp =>
        statement.setTimestamp(index(), v)
      case v: java.sql.Date =>
        statement.setDate(index(), v)
      case v: java.util.Date =>
        statement.setTimestamp(index(), new java.sql.Timestamp(v.getTime))
      case v: java.time.LocalDate =>
        statement.setDate(index(), java.sql.Date.valueOf(v))
      case v: java.time.LocalTime =>
        statement.setTime(index(), java.sql.Time.valueOf(v))
      case v: java.time.LocalDateTime =>
        statement.setTimestamp(index(), java.sql.Timestamp.valueOf(v))
      case v: java.time.Instant =>
        statement.setTimestamp(index(), java.sql.Timestamp.from(v))
      case v: java.util.TimeZone =>
        statement.setString(index(), v.getID)

      // Binary data
      case v: Array[Byte] =>
        statement.setBytes(index(), v)
      case v: java.sql.Blob =>
        statement.setBlob(index(), v)
      case v: java.io.InputStream =>
        statement.setBinaryStream(index(), v)

      // Character streams
      case v: java.io.Reader => statement.setCharacterStream(index(), v)

      // Other SQL types
      case v: java.sql.Array =>
        statement.setArray(index(), v)
      case v: java.sql.Ref =>
        statement.setRef(index(), v)
      case v: java.net.URL =>
        statement.setURL(index(), v)

      // Scala specific types
      case v: Option[_] =>
        v match {
          case Some(value) =>
            // Recursively handle the wrapped value
            value match {
              case null =>
                statement.setNull(index(), java.sql.Types.NULL)
              case _ => // Will be handled by the next iteration of pattern matching
            }
          case None =>
            statement.setNull(index(), java.sql.Types.NULL)
        }
      // Fallback for AnyRef types
      case v: AnyRef =>
        statement.setObject(index(), v)

      // Final fallback - convert to string representation
      case v: Any =>
        statement.setString(index(), v.toString)
    }
  }

  /** Creates a new HikariDataSource instance
    *
    * @param config
    *   configuration for HikariCP
    * @param binder
    *   function to bind parameters to prepared statement
    * @return
    *   instance of HikariDataSource
    */
  def apply(config: hikari.HikariConfig)(using binder: StatementBinder = defaultStatementBinder): HikariDataSource = {
    new HikariDataSource(new hikari.HikariDataSource(config), binder)
  }
}
