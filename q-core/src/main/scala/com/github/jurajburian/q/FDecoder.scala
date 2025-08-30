package com.github.jurajburian.q

import scala.deriving.Mirror

/** FEncoder is used to derive a typed encoder from F -> a case class or named tuple
  *
  * @tparam F
  *   type of the row, for example [[java.sql.ResultSet]]
  */
trait FDecoder[F] {

  /** Type alias for a function that decodes a row of type F into an instance of type T
    *
    * @tparam T
    *   the type of the object to be created from the row
    */
  type TypedDecoder[T] = F => T

  /** derive typed encoder from an [[scala.Product]] e.t. case class or for named tuple
    *
    * @param fieldNameMap
    *   map from case class attribute name to column name
    * @param columnNameMapper
    *   function that transform case class attribute name to column name in database
    * @param mirror
    *   [[scala.Product]] mirror
    * @tparam T
    *   concrete case class type
    * @return
    *   instance of [[TypedDecoder]]
    */
  inline def derive[T](
      fieldNameMap: FieldNameMap = Map.empty
  )(using
      mirror: Mirror.ProductOf[T],
      columnNameMapper: ColumnNameMapper = ColumnNameMapper.noTransform
  ): TypedDecoder[T] = {

    if (!(Macros.isNamedTuple[T] || Macros.isProduct[T])) {
      Macros.reportError("Type T is not a Product (case class) nor NamedTuple")
    }

    val fieldNames = if (Macros.isProduct[T]) {
      Macros
        .getProductFieldNames[T]
        .map {
          case Left(fieldName)  => fieldName
          case Right(fieldName) => fieldNameMap.getOrElse(fieldName, columnNameMapper(fieldName))
        }
    } else {
      Macros
        .getNamedTupleFieldNames[T]
        .map(fieldName => fieldNameMap.getOrElse(fieldName, columnNameMapper(fieldName)))
    }
    val encoders = if (Macros.isProduct[T]) {
      Macros.summonEncodersForProduct[T, F]
    } else {
      Macros.summonEncodersForNamedTuple[T, F]
    }
    (rs: F) => {
      val values = fieldNames.zip(encoders).map { case (fieldName, encoder) =>
        encoder.from(rs, fieldName)
      }
      mirror.fromProduct(Tuple.fromArray(values.toArray))
    }
  }
}
