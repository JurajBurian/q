package com.github.jurajburian.q.jdbc

import com.github.jurajburian.q.*

import scala.collection.immutable

/** Helper method to create safe SQL "IN" clause that handles empty collections. If the collection is empty, it
  * generates a tautology (1=1) or contradiction (1=0) based on the `empty` flag.
  *
  * @param columnName
  *   The name of the column to be used in the "IN" clause.
  * @param value
  *   The collection of values for the "IN" clause. Can be an Array or Iterable.
  * @param empty
  *   If true and the collection is empty, generates "1=1". If false, generates "1=0".
  * @tparam Q
  *   The type of the resulting SQL query.
  * @return
  *   A SQL query representing the safe "IN" clause.
  */
def in(columnName: String, value: (Array[?] | Iterable[?]), empty: Boolean = false): Q = {
  val it = value match {
    case arr: Array[?]   => immutable.ArraySeq.unsafeWrapArray(arr)
    case it: Iterable[?] => it
  }
  if (it.isEmpty) {
    if (empty) { q"1=1" }
    else { q"1=0" }
  } else {
    q"${columnName.!} in(${value.?})"
  }
}
