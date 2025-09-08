package com.github.jurajburian.q

import scala.annotation.tailrec

/** A function that maps column names from case class atribute names to database column names.
  */
trait ColumnNameMapper extends (String => String)

object ColumnNameMapper {

  val noTransform: ColumnNameMapper = (p: String) => p

  /** Converts camelCase to snake_case
    * @return
    */
  val camelToSnake: ColumnNameMapper = (p: String) => {
    // not a nice code, but well performing
    @tailrec
    def rec(idx: Int = 0, res: StringBuilder = new StringBuilder): String = if (p.length > idx) {
      val f = p(idx)
      if (f.isUpper) {
        if (idx == 0) res.append(f.toLower) else res.append('_').append(f.toLower)
      } else {
        res.append(f)
      }
      rec(idx + 1, res)
    } else res.result()
    rec()
  }
}
