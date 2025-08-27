package com.github.jurajburian.q

import com.github.jurajburian.q.jdbc.SqlRowEncoder

import java.sql.ResultSet
import scala.language.postfixOps

package object jdbc {

  given ColumnEncoder[Int, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Int = rs.getInt(fieldName)
    def as(x: Any): Int = x.asInstanceOf[Int]
  }

  given ColumnEncoder[Long, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Long = rs.getLong(fieldName)
    def as(x: Any): Long = x.asInstanceOf[Long]
  }

  given ColumnEncoder[Short, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Short = rs.getShort(fieldName)
    def as(x: Any): Short = x.asInstanceOf[Short]
  }

  given ColumnEncoder[Byte, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Byte = rs.getByte(fieldName)
    def as(x: Any): Byte = x.asInstanceOf[Byte]
  }

  given ColumnEncoder[Float, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Float = rs.getFloat(fieldName)
    def as(x: Any): Float = x.asInstanceOf[Float]
  }

  given ColumnEncoder[Double, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Double = rs.getDouble(fieldName)
    def as(x: Any): Double = x.asInstanceOf[Double]
  }

  given ColumnEncoder[Boolean, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Boolean = rs.getBoolean(fieldName)
    def as(x: Any): Boolean = x.asInstanceOf[Boolean]
  }

  given ColumnEncoder[String, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): String = rs.getString(fieldName)
    def as(x: Any): String = x.asInstanceOf[String]
  }

  given ColumnEncoder[BigDecimal, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): BigDecimal = rs.getBigDecimal(fieldName)
    def as(x: Any): BigDecimal = x.asInstanceOf[BigDecimal]
  }

  given ColumnEncoder[java.sql.Date, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.sql.Date = rs.getDate(fieldName)
    def as(x: Any): java.sql.Date = x.asInstanceOf[java.sql.Date]
  }

  given ColumnEncoder[java.sql.Time, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.sql.Time = rs.getTime(fieldName)
    def as(x: Any): java.sql.Time = x.asInstanceOf[java.sql.Time]
  }

  given ColumnEncoder[java.sql.Timestamp, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.sql.Timestamp = rs.getTimestamp(fieldName)
    def as(x: Any): java.sql.Timestamp = x.asInstanceOf[java.sql.Timestamp]
  }

  given ColumnEncoder[java.time.LocalDate, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.time.LocalDate = rs.getDate(fieldName).toLocalDate
    def as(x: Any): java.time.LocalDate = x.asInstanceOf[java.time.LocalDate]
  }

  given ColumnEncoder[java.time.LocalTime, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.time.LocalTime = rs.getTime(fieldName).toLocalTime
    def as(x: Any): java.time.LocalTime = x.asInstanceOf[java.time.LocalTime]
  }

  given ColumnEncoder[java.time.LocalDateTime, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.time.LocalDateTime = rs.getTimestamp(fieldName).toLocalDateTime
    def as(x: Any): java.time.LocalDateTime = x.asInstanceOf[java.time.LocalDateTime]
  }

  given ColumnEncoder[java.time.OffsetTime, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.time.OffsetTime =
      rs.getObject(fieldName, classOf[java.time.OffsetTime])
    def as(x: Any): java.time.OffsetTime = x.asInstanceOf[java.time.OffsetTime]
  }

  given ColumnEncoder[java.time.OffsetDateTime, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.time.OffsetDateTime =
      rs.getObject(fieldName, classOf[java.time.OffsetDateTime])
    def as(x: Any): java.time.OffsetDateTime = x.asInstanceOf[java.time.OffsetDateTime]
  }

  // Option type support (for nullable columns)
  given [T](using inner: ColumnEncoder[T, ResultSet]): ColumnEncoder[Option[T], ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Option[T] = {
      val value = rs.getObject(fieldName)
      if (rs.wasNull) None else Some(inner.from(rs, fieldName))
    }
    def as(x: Any): Option[T] = {
      if (x == null) None else Some(inner.as(x))
    }
  }

  /**
   * Tuple 2 support
   */
  given [X, Y](using
               x: SqlRowEncoder.TypedEncoder[X],
               y: SqlRowEncoder.TypedEncoder[Y]): SqlRowEncoder.TypedEncoder[(X, Y)] with {
    def apply(rs: ResultSet): (X, Y) = (x(rs), y(rs))
  }

  /**
   * Tuple 3 support
   */
  given [X, Y, Z](using
                   x: SqlRowEncoder.TypedEncoder[X],
                   y: SqlRowEncoder.TypedEncoder[Y],
                   z: SqlRowEncoder.TypedEncoder[Z]): SqlRowEncoder.TypedEncoder[(X, Y, Z)] with {
    def apply(rs: ResultSet): (X, Y, Z) = (x(rs), y(rs), z(rs))
  }
}
