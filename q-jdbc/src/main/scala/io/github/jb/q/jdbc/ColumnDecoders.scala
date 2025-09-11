package io.github.jb.q.jdbc

import io.github.jb.q.ColumnDecoder
import java.sql.ResultSet

object ColumnDecoders {

  given ColumnDecoder[Int, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Int = rs.getInt(fieldName)
    def as(x: Any): Int = x.asInstanceOf[Int]
  }

  given ColumnDecoder[Long, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Long = rs.getLong(fieldName)
    def as(x: Any): Long = x.asInstanceOf[Long]
  }

  given ColumnDecoder[Short, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Short = rs.getShort(fieldName)
    def as(x: Any): Short = x.asInstanceOf[Short]
  }

  given ColumnDecoder[Byte, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Byte = rs.getByte(fieldName)
    def as(x: Any): Byte = x.asInstanceOf[Byte]
  }

  given ColumnDecoder[Float, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Float = rs.getFloat(fieldName)
    def as(x: Any): Float = x.asInstanceOf[Float]
  }

  given ColumnDecoder[Double, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Double = rs.getDouble(fieldName)
    def as(x: Any): Double = x.asInstanceOf[Double]
  }

  given ColumnDecoder[Boolean, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Boolean = rs.getBoolean(fieldName)
    def as(x: Any): Boolean = x.asInstanceOf[Boolean]
  }

  given ColumnDecoder[String, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): String = rs.getString(fieldName)
    def as(x: Any): String = x.asInstanceOf[String]
  }

  given ColumnDecoder[BigDecimal, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): BigDecimal = rs.getBigDecimal(fieldName)
    def as(x: Any): BigDecimal = x.asInstanceOf[BigDecimal]
  }

  given ColumnDecoder[java.sql.Date, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.sql.Date = rs.getDate(fieldName)
    def as(x: Any): java.sql.Date = x.asInstanceOf[java.sql.Date]
  }

  given ColumnDecoder[java.sql.Time, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.sql.Time = rs.getTime(fieldName)
    def as(x: Any): java.sql.Time = x.asInstanceOf[java.sql.Time]
  }

  given ColumnDecoder[java.sql.Timestamp, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.sql.Timestamp = rs.getTimestamp(fieldName)
    def as(x: Any): java.sql.Timestamp = x.asInstanceOf[java.sql.Timestamp]
  }

  given ColumnDecoder[java.time.LocalDate, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.time.LocalDate = rs.getDate(fieldName).toLocalDate
    def as(x: Any): java.time.LocalDate = x.asInstanceOf[java.time.LocalDate]
  }

  given ColumnDecoder[java.time.LocalTime, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.time.LocalTime = rs.getTime(fieldName).toLocalTime
    def as(x: Any): java.time.LocalTime = x.asInstanceOf[java.time.LocalTime]
  }

  given ColumnDecoder[java.time.LocalDateTime, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.time.LocalDateTime = rs.getTimestamp(fieldName).toLocalDateTime
    def as(x: Any): java.time.LocalDateTime = x.asInstanceOf[java.time.LocalDateTime]
  }

  given ColumnDecoder[java.time.OffsetTime, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.time.OffsetTime =
      rs.getObject(fieldName, classOf[java.time.OffsetTime])
    def as(x: Any): java.time.OffsetTime = x.asInstanceOf[java.time.OffsetTime]
  }

  given ColumnDecoder[java.time.OffsetDateTime, ResultSet] with {
    def from(rs: ResultSet, fieldName: String): java.time.OffsetDateTime =
      rs.getObject(fieldName, classOf[java.time.OffsetDateTime])
    def as(x: Any): java.time.OffsetDateTime = x.asInstanceOf[java.time.OffsetDateTime]
  }

  // Option type support (for nullable columns)
  given [T](using inner: ColumnDecoder[T, ResultSet]): ColumnDecoder[Option[T], ResultSet] with {
    def from(rs: ResultSet, fieldName: String): Option[T] = {
      val value = rs.getObject(fieldName)
      if (rs.wasNull) None else Some(inner.from(rs, fieldName))
    }
    def as(x: Any): Option[T] = {
      if (x == null) None else Some(inner.as(x))
    }
  }
}