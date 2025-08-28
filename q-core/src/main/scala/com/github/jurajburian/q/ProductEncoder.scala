package com.github.jurajburian.q

import scala.deriving.Mirror
import scala.quoted.*
import com.github.jurajburian.q.ColumnEncoder

type FieldNameMap = Map[String, String]

trait ProductEncoder[F] {

  type TypedEncoder[T] = F => T

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
  inline def derive[T <: Product](
      fieldNameMap: FieldNameMap = Map.empty
  )(using
      mirror: Mirror.ProductOf[T],
      columnNameMapper: ColumnNameMapper = ColumnNameMapper.noTransform
  ): TypedEncoder[T] = {
    val fieldNames =
      EncoderMacros
        .getProductFieldNames[T]
        .map {
          case Left(fieldName)  => fieldName
          case Right(fieldName) => fieldNameMap.getOrElse(fieldName, columnNameMapper(fieldName))
        }
    val encoders = EncoderMacros.summonEncodersForProduct[T, F]
    (rs: F) => {
      val values = fieldNames.zip(encoders).map { case (fieldName, encoder) =>
        encoder.from(rs, fieldName)
      }
      mirror.fromProduct(Tuple.fromArray(values.toArray))
    }
  }
}

trait NamedTupleEncoder[F] {

  type TypedEncoder[T] = F => T

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
  inline def derive[T](
      fieldNameMap: FieldNameMap = Map.empty
  )(using
      mirror: Mirror.ProductOf[T],
      columnNameMapper: ColumnNameMapper = ColumnNameMapper.noTransform
  ): TypedEncoder[T] = {
    val fieldNames = EncoderMacros.getNamedTupleFieldNames[T]
    val encoders = EncoderMacros.summonEncodersForNamedTuple[T, F]

    (rs: F) => {
      val values = fieldNames.zip(encoders).map { case (fieldName, encoder) =>
        encoder.from(rs, fieldName)
      }
      mirror.fromProduct(Tuple.fromArray(values.toArray))
    }
  }
}

object EncoderMacros {

  inline def summonEncodersForProduct[T <: Product, F](using mirror: Mirror.ProductOf[T]): List[ColumnEncoder[?, F]] =
    ${ summonEncodersForProductImpl[T, F]('mirror) }

  private def summonEncodersForProductImpl[T <: Product: Type, F: Type](
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

  /** @param mirror
    *   *
    * @tparam T
    * @return
    *   list of Either values, left value can't be transformed, Right value is open for transformation
    */
  inline def getProductFieldNames[T <: Product](using mirror: Mirror.ProductOf[T]): List[Either[String, String]] =
    ${ getProductFieldNamesImpl[T]('mirror) }

  private def getProductFieldNamesImpl[T <: Product: Type](
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

  inline def summonEncodersForNamedTuple[T, F]: List[ColumnEncoder[?, F]] =
    ${ summonEncodersForNamedTupleImpl[T, F] }

  private def summonEncodersForNamedTupleImpl[T: Type, F: Type](using
      Quotes
  ): Expr[List[ColumnEncoder[?, F]]] = {
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
      val encoderType = TypeRepr.of[ColumnEncoder].appliedTo(List(fieldType, TypeRepr.of[F]))
      Implicits.search(encoderType) match {
        case result: ImplicitSearchSuccess => result.tree.asExprOf[ColumnEncoder[?, F]]
        case _ => report.errorAndAbort(s"Could not summon ColumnEncoder for type: ${fieldType.show}")
      }
    }

    Expr.ofList(encoders)
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
}
