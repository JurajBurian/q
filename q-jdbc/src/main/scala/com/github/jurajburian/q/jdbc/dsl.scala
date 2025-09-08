package com.github.jurajburian.q.jdbc

import com.github.jurajburian.q.*

import scala.NamedTuple.NamedTuple
import scala.annotation.{nowarn, targetName}
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
private def in(columnName: String, value: (Array[?] | Iterable[?]), empty: Boolean = false): Q = {
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

/** Extension method for columnName to create safe SQL "IN" clause that handles empty collections. Example: {{ val q =
  * "select * from table where id in ${columnName.inOrFalse(Array(1, 2, 3))}" }}
  */
extension (columnName: String) {
  def inOrFalse(value: (Array[?] | Iterable[?])): Q = in(columnName, value, empty = false)
  def inOrTrue(value: (Array[?] | Iterable[?])): Q = in(columnName, value, empty = true)
}

/** extension method that converts java.time.ZoneId to SQL time zone using: " at time zone 'TimeZoneId'" so value is not
  * bind but is expanded in select directly
  */
extension (timeZone: java.time.ZoneId) {
  def at = q"at time zone ${timeZone.getId}"
}

/** @param excludes
  *   excluded fields
  * @param fieldNameMap
  *   custom mapping if necessary
  * @param columnNameMapper
  *   conversion to database attributes for example : camelCase to snake_case
  * @tparam T
  *   original type for projected fields
  * @return
  *   string of comma separated attributes
  */
inline def attrProjection[T](excludes: Set[String] = Set.empty, fieldNameMap: FieldNameMap = Map.empty)(using
    columnNameMapper: ColumnNameMapper = ColumnNameMapper.noTransform
) = {
  val excl = excludes.toSet
  @nowarn("msg=New anonymous class definition will be duplicated at each inline site")
  val names = Macros.getAttributeNames[T].collect {
    case (fn: String, mfn: String) if !excl.contains(fn) => mfn
    case fn: String if !excl.contains(fn)                => fieldNameMap.getOrElse(fn, columnNameMapper(fn))
  }
  names.mkString(",").!
}

extension [T](it: Iterable[T]) {
  @targetName("namesOfTuple")
  def ?? : Q = {
    val q = it
      .map {
        case p: Product => q",(${p.productIterator.?})"
        case other =>
          throw new IllegalArgumentException(s"Unsupported type: ${other.getClass}")
      }
      .reduce(_ + _)
    q.copy(q.query.tail)
  }
}
