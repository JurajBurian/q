package com.github.jurajburian.q.jdbc

import com.github.jurajburian.q.*

import java.sql.PreparedStatement
import java.time.Instant
// TODO revision of this mechanism
/** Represents a value that can be bound to a SQL prepared statement this give us ability to wrap and bind custom types.
  * Two possible examples how to implement
  * {{{
  *   case class Count(v: Long) extends SqlBindable {
  *   override def bind(statement: PreparedStatement, index: => Int): Unit =
  *     statement.setLong(index, v)
  * }
  *
  * case class TimeAtTimezone(t: java.time.Instant, timezone: java.util.TimeZone) extends SqlBindable with QTransformable {
  *   override def bind(statement: PreparedStatement, index: => Int): Unit = {
  *     statement.setTimestamp(index, java.sql.Timestamp.from(t))
  *     statement.setString(index, timezone.getID)
  *   }
  *   override def transform(transformer: Any => List[Any], binder: QBinder): List[Any] = {
  *     binder.bind(this)
  *     "(? at timezone ?)" :: Nil
  *   }
  * }
  * }}}
  */
trait SqlBindable {

  /** bing self to statement
    * @param statement
    *   statement
    * @param index
    *   index under value is bind
    */
  def bind(statement: PreparedStatement, index: => Int): Unit
}
