package com.github.jurajburian.q

import scala.deriving.Mirror
import scala.quoted.*
import com.github.jurajburian.q.ColumnDecoder

type FieldNameMap = Map[String, String]

/** FEncoder is used to derive a typed encoder from F -> a case class or named tuple
  *
  * @tparam F
  *   type of the row, for example [[java.sql.ResultSet]]
  */
trait FDecoder[F] {

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

object Macros {

  inline def isProduct[T]: Boolean = ${ isProductImpl[T] }

  private def isProductImpl[T: Type](using Quotes): Expr[Boolean] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]
    val isProduct = tpe <:< TypeRepr.of[Product]
    Expr(isProduct)
  }

  inline def summonEncodersForProduct[T, F](using mirror: Mirror.ProductOf[T]): List[ColumnDecoder[?, F]] =
    ${ summonEncodersForProductImpl[T, F]('mirror) }

  private def summonEncodersForProductImpl[T: Type, F: Type](
      mirror: Expr[Mirror.ProductOf[T]]
  )(using Quotes): Expr[List[ColumnDecoder[?, F]]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val classSymbol = tpe.typeSymbol
    val paramSyms = classSymbol.primaryConstructor.paramSymss.flatten
    val fieldTypes = paramSyms.map(_.tree match {
      case v: ValDef => v.tpt.tpe
      case p         => report.errorAndAbort(s"Unexpected tree for param: ${p.show}")
    })
    val encoders = fieldTypes.map { fieldType =>
      val encoderType = TypeRepr.of[ColumnDecoder].appliedTo(List(fieldType, TypeRepr.of[F]))
      Implicits.search(encoderType) match {
        case result: ImplicitSearchSuccess => result.tree.asExprOf[ColumnDecoder[?, F]]
        case _ => report.errorAndAbort(s"Could not summon ColumnEncoder for type: ${fieldType.show}")
      }
    }

    Expr.ofList(encoders)
  }

  /** @param mirror
    *   *
    * @tparam T
    * @return
    *   list of Either values, left value can't be transformed, Right value is open for transformation
    */
  inline def getProductFieldNames[T](using mirror: Mirror.ProductOf[T]): List[Either[String, String]] =
    ${ getProductFieldNamesImpl[T]('mirror) }

  private def getProductFieldNamesImpl[T: Type](
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
        '{ Right[String, String]($fieldNameExpr) }
      }
    }
    val seq: Expr[Seq[Either[String, String]]] = Expr.ofSeq(tuples)
    '{ $seq.toList }
  }

  inline def summonEncodersForNamedTuple[T, F]: List[ColumnDecoder[?, F]] =
    ${ summonEncodersForNamedTupleImpl[T, F] }

  private def summonEncodersForNamedTupleImpl[T: Type, F: Type](using
      Quotes
  ): Expr[List[ColumnDecoder[?, F]]] = {
    import quotes.reflect.*

    val tr = TypeRepr.of[T].dealias
    val fieldTypes = tr match {
      case AppliedType(tpe, args) if tpe.typeSymbol.fullName.startsWith("scala.NamedTuple") =>
        args.tail.head match {
          case AppliedType(_, references) => references
          case _ =>
            report.errorAndAbort(s"Type ${tr.show} is not a NamedTuple type")
        }
      case _ =>
        report.errorAndAbort(s"Type ${tr.show} is not a NamedTuple type")
    }
    val encoders = fieldTypes.map { fieldType =>
      val encoderType = TypeRepr.of[ColumnDecoder].appliedTo(List(fieldType, TypeRepr.of[F]))
      Implicits.search(encoderType) match {
        case result: ImplicitSearchSuccess => result.tree.asExprOf[ColumnDecoder[?, F]]
        case _ => report.errorAndAbort(s"Could not summon ColumnEncoder for type: ${fieldType.show}")
      }
    }

    Expr.ofList(encoders)
  }

  inline def isNamedTuple[T]: Boolean =
    ${ isNamedTupleImpl[T] }

  private def isNamedTupleImpl[T: Type](using Quotes): Expr[Boolean] = {
    import quotes.reflect.*
    val tr = TypeRepr.of[T].dealias
    val isNamedTuple = tr match {
      case AppliedType(tpe, _) if tpe.typeSymbol.fullName.startsWith("scala.NamedTuple") => true
      case _                                                                             => false
    }
    Expr(isNamedTuple)
  }

  inline def getNamedTupleFieldNames[T]: List[String] =
    ${ getNamedTupleFieldNamesImpl[T] }

  private def getNamedTupleFieldNamesImpl[T: Type](using Quotes): Expr[List[String]] = {
    import quotes.reflect.*
    val tr = TypeRepr.of[T].dealias
    val names = tr match {
      case AppliedType(tpe, args) if tpe.typeSymbol.fullName.startsWith("scala.NamedTuple") =>
        args.head match {
          case t: TypeRepr =>
            t.typeArgs.toList.map { p =>
              Expr(p.asInstanceOf[ConstantType].constant.value.toString())
            }
        }
      case _ =>
        report.errorAndAbort(s"Type ${tr.show} is not a NamedTuple type")
    }
    val seq = Expr.ofSeq(names)
    '{ $seq.toList }
  }

  inline def reportError(err: String): Unit = ${ reportErrorImpl('err) }

  private def reportErrorImpl(err: Expr[String])(using Quotes): Expr[Unit] = {
    import quotes.reflect.*
    report.errorAndAbort(err.valueOrAbort)
    '{ () }
  }

}
