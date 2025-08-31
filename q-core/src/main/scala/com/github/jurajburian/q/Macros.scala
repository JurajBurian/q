package com.github.jurajburian.q

import scala.deriving.Mirror
import scala.quoted.*

private[q] object MacrosUtils {

  def isProduct(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe.dealias <:< TypeRepr.of[Product]
  }

  def isNamedTuple(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    val tr = tpe.dealias
    tr match {
      case AppliedType(tpe, _) if tpe.typeSymbol.fullName.startsWith("scala.NamedTuple") => true
      case _                                                                             => false
    }
  }

  def getNamedTupleFieldNames(using Quotes)(tpe: quotes.reflect.TypeRepr): List[String] = {
    import quotes.reflect.*
    tpe.dealias match {
      case AppliedType(tpe, args) if tpe.typeSymbol.fullName.startsWith("scala.NamedTuple") =>
        args.head match {
          case t: TypeRepr =>
            t.typeArgs.toList.map { p =>
              p.asInstanceOf[ConstantType].constant.value.toString()
            }
        }
      case _ =>
        report.errorAndAbort(s"Type ${tpe.show} is not a NamedTuple type")
    }
  }

  def getProductFieldNames[T: Type](using Quotes)(tpe: quotes.reflect.TypeRepr): List[String | (String, String)] = {
    import quotes.reflect.*
    val annotation = TypeRepr.of[QAlias].typeSymbol
    val classSymbol = tpe.classSymbol.get
    tpe.typeSymbol.primaryConstructor.paramSymss.flatten.map { sym =>
      val fieldName = sym.name.toString()
      sym
        .getAnnotation(annotation)
        .map { case Apply(_, list) =>
          val c = list.head.asInstanceOf[Literal].constant.value.toString
          (fieldName, c.toString)
        }
        .getOrElse(fieldName)
    }
  }
}

object Macros {

  inline def isProduct[T]: Boolean = ${ isProductImpl[T] }

  private def isProductImpl[T: Type](using Quotes): Expr[Boolean] = {
    import quotes.reflect.*
    Expr(MacrosUtils.isProduct(TypeRepr.of[T]))
  }

  inline def summonEncodersForProduct[T, F]: List[ColumnDecoder[?, F]] =
    ${ summonEncodersForProductImpl[T, F] }

  private def summonEncodersForProductImpl[T: Type, F: Type](using Quotes): Expr[List[ColumnDecoder[?, F]]] = {

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

  /** @tparam T
    *   product type
    * @return
    *   list of Either values, left value can't be transformed, Right value is open for transformation
    */
  inline def getProductFieldNames[T]: List[String | (String, String)] =
    ${ getProductFieldNamesImpl[T] }

  private def getProductFieldNamesImpl[T: Type](using Quotes): Expr[List[String | (String, String)]] = {
    import quotes.reflect.*
    val names = MacrosUtils.getProductFieldNames(TypeRepr.of[T]).map {
      case p: String           => Expr(p)
      case x: (String, String) => Expr(x)
    }
    val seq = Expr.ofSeq(names)
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
    Expr(MacrosUtils.isNamedTuple(TypeRepr.of[T]))
  }

  inline def getNamedTupleFieldNames[T]: List[String] =
    ${ getNamedTupleFieldNamesImpl[T] }

  private def getNamedTupleFieldNamesImpl[T: Type](using Quotes): Expr[List[String]] = {
    import quotes.reflect.*
    val names = MacrosUtils.getNamedTupleFieldNames(TypeRepr.of[T]).map(Expr(_))
    val seq = Expr.ofSeq(names)
    '{ $seq.toList }
  }

  inline def getAttributeNames[T]: List[(String | (String, String))] = ${ getAttributeNamesImpl[T] }

  private def getAttributeNamesImpl[T: Type](using Quotes): Expr[List[(String | (String, String))]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]
    val names = if (MacrosUtils.isNamedTuple(tpe)) {
      MacrosUtils.getNamedTupleFieldNames(tpe).map { Expr(_) }
    } else if (MacrosUtils.isProduct(tpe)) {
      MacrosUtils.getProductFieldNames(tpe).map {
        case p: String           => Expr(p)
        case x: (String, String) => Expr(x)
      }
    } else {
      report.errorAndAbort("Type T is not a Product (case class) nor NamedTuple")
      Nil
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
