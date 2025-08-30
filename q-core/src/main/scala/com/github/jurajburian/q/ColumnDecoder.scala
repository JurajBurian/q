package com.github.jurajburian.q

/** RowEncoder is used to convert a row from the database into a specific type T
 *
 * @tparam T
 *   type of the object to be created from the row
 * @tparam F
 *   type of the row, for example  [[java.sql.ResultSet]]
 */
trait ColumnDecoder[T, F]  {

  /** Converts a row into an instance of type T
   *
   * @param row
   *   the row to convert
   * @param fieldName
   *   the name of the field to extract from the row
   * @tparam F type of the row, for example  [[java.sql.ResultSet]]
   * @return
   *   an instance of type T created from the row
   */
  def from(row: F, fieldName: String): T

  /** Converts a value of type Any into an instance of type T
   *
   * @param x
   *   the value to convert
   * @return
   *   an instance of type T created from the value
   */
  def as(x: Any): T
}