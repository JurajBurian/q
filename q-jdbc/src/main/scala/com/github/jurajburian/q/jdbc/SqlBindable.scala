package com.github.jurajburian.q.jdbc

import com.github.jurajburian.q.*
import java.sql.PreparedStatement


/** Represents a value that can be bound to a SQL prepared statement
 */
trait SqlBindable  {
  def bind(statement:PreparedStatement, index:Int): Unit
}
