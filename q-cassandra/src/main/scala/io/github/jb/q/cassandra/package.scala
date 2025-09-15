package io.github.jb.q

import com.datastax.oss.driver.api.core.cql.Row

package object cassandra {

  given ColumnDecoder[Int, Row] with {
    def from(r: Row, fieldName: String): Int =
      r.getInt(fieldName)

    def as(x: Any): Int = x.asInstanceOf[Int]
  }

  given ColumnDecoder[Long, Row] with {
    def from(r: Row, fieldName: String): Long = r.getLong(fieldName)

    def as(x: Any): Long = x.asInstanceOf[Long]
  }

  given ColumnDecoder[Short, Row] with {
    def from(r: Row, fieldName: String): Short = r.getShort(fieldName)

    def as(x: Any): Short = x.asInstanceOf[Short]
  }

  given ColumnDecoder[Byte, Row] with {
    def from(r: Row, fieldName: String): Byte = r.getByte(fieldName)

    def as(x: Any): Byte = x.asInstanceOf[Byte]
  }

  given ColumnDecoder[Float, Row] with {
    def from(r: Row, fieldName: String): Float = r.getFloat(fieldName)

    def as(x: Any): Float = x.asInstanceOf[Float]
  }

  given ColumnDecoder[Double, Row] with {
    def from(r: Row, fieldName: String): Double = {
      println("get: " + r.getDouble(fieldName))
      r.getDouble(fieldName)
    }

    def as(x: Any): Double = x.asInstanceOf[Double]
  }

  given ColumnDecoder[Boolean, Row] with {
    def from(r: Row, fieldName: String): Boolean = r.getBoolean(fieldName)

    def as(x: Any): Boolean = x.asInstanceOf[Boolean]
  }

  given ColumnDecoder[String, Row] with {
    def from(r: Row, fieldName: String): String = r.getString(fieldName)
    def as(x: Any): String = x.asInstanceOf[String]
  }

  given ColumnDecoder[BigDecimal, Row] with {
    def from(r: Row, fieldName: String): BigDecimal = r.getBigDecimal(fieldName)

    def as(x: Any): BigDecimal = x.asInstanceOf[BigDecimal]
  }

  given ColumnDecoder[java.time.Instant, Row] with {
    def from(r: Row, fieldName: String): java.time.Instant = r.getInstant(fieldName)
    def as(x: Any): java.time.Instant = x.asInstanceOf[java.time.Instant]
  }


}
