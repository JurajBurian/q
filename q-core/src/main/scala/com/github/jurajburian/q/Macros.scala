package com.github.jurajburian.q

import scala.deriving.Mirror
import scala.quoted.*

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
    *   a mirror
    * @tparam T
    *   product type
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
