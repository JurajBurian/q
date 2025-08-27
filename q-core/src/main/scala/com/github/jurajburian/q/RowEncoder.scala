package com.github.jurajburian.q

import scala.deriving.Mirror
import scala.quoted.*
import com.github.jurajburian.q.ColumnEncoder

import scala.annotation.tailrec // Import your encoder
// import com.github.jurajburian.sdbc.SqlName   // Import or define SqlName annotation

trait RowEncoder[F] {

  type FieldNameMap = Map[String, String]
  type TypedEncoder[T] = F => T

  given ColumnNameMapper = ColumnNameMapper.noTransform

  /** derive typed encoder from an [[scala.Product]] e.t. case class
    * @param fieldNameMap
    *   map from case class attribute name to column name
    * @param columnNameMapper
    *   function that transform case class attribute name to column name in database
    * @param mirror
    *   [[scala.Product]] mirror
    * @tparam T
    *   concrete case class type
    * @return
    *   instance of [[TypedEncoder]]
    */
  inline def deriveFromResultSet[T <: Product](
      fieldNameMap: FieldNameMap = Map.empty
  )(using
      mirror: Mirror.ProductOf[T],
      columnNameMapper: ColumnNameMapper = ColumnNameMapper.noTransform
  ): TypedEncoder[T] = {
    val fieldNames =
      RowEncoderMacros.getFieldNames[T]
      .map{
        case Left(fieldName) => fieldName
        case Right(fieldName) => fieldNameMap.getOrElse(fieldName, columnNameMapper(fieldName)) 
      }
    val encoders = RowEncoderMacros.summonEncoders[T, F]
    (rs: F) => {
      val values = fieldNames.zip(encoders).map { case (fieldName, encoder) =>
        encoder.from(rs, fieldName)
      }
      mirror.fromProduct(Tuple.fromArray(values.toArray))
    }
  }
}

object RowEncoderMacros {

  inline def summonEncoders[T <: Product, F](using mirror: Mirror.ProductOf[T]): List[ColumnEncoder[?, F]] =
    ${ summonEncodersImpl[T, F]('mirror) }

  private def summonEncodersImpl[T <: Product: Type, F: Type](
      mirror: Expr[Mirror.ProductOf[T]]
  )(using Quotes): Expr[List[ColumnEncoder[?, F]]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val classSymbol = tpe.typeSymbol
    val paramSyms = classSymbol.primaryConstructor.paramSymss.flatten
    val fieldTypes = paramSyms.map(_.tree match {
      case v: ValDef => v.tpt.tpe
      case p         => report.errorAndAbort(s"Unexpected tree for param: ${p.show}")
    })
    val encoders = fieldTypes.map { fieldType =>
      val encoderType = TypeRepr.of[ColumnEncoder].appliedTo(List(fieldType, TypeRepr.of[F]))
      Implicits.search(encoderType) match {
        case result: ImplicitSearchSuccess => result.tree.asExprOf[ColumnEncoder[?, F]]
        case _ => report.errorAndAbort(s"Could not summon ColumnEncoder for type: ${fieldType.show}")
      }
    }

    Expr.ofList(encoders)
  }

  /**
   * @param mirror   *
   * @tparam T
   * @return list of Either values, left value can't be transformed, Right value is open for transformation
   */
  inline def getFieldNames[T <: Product](using mirror: Mirror.ProductOf[T]): List[Either[String, String]] =
    ${ getFieldNamesImpl[T]('mirror) }

  private def getFieldNamesImpl[T <: Product: Type](
      mirror: Expr[Mirror.ProductOf[T]]
  )(using Quotes): Expr[List[Either[String, String]]] = {
    import quotes.reflect.*
    val annot = TypeRepr.of[QAlias].typeSymbol
    val classSymbol = TypeRepr.of[T].classSymbol.get
    val tuples = TypeRepr.of[T].typeSymbol.primaryConstructor.paramSymss.flatten.map { sym =>
      val fieldNameExpr = Expr(sym.name.asInstanceOf[String])
      if (sym.hasAnnotation(annot)) {
        val annotExpr = sym.getAnnotation(annot).get.asExprOf[QAlias]
        '{ Left[String, String]($annotExpr.name) }
      } else {
        '{Right[String, String]($fieldNameExpr)}
      }
    }
    val seq: Expr[Seq[Either[String, String]]] = Expr.ofSeq(tuples)
    '{ $seq.toList }
  }
}
