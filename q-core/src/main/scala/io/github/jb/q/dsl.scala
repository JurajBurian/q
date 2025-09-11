package io.github.jb.q

import scala.annotation.targetName
import scala.collection.immutable

/** not bound value. Value is expanded in the SQL statement, but not bound.
  *
  * @param value
  *   value
  */
private[q] class NBV(value: Any) extends QTransformable {
  override def transform(transformer: Any => List[Any], binder: QBinder): List[Any] = List(value)
}

/** not bound value
  *
  * @param value
  *   input value
  */
extension (value: Any) {
  @targetName("bang")
  def ! : NBV = new NBV(value)
}

val QNothing = "".!

/** Bind iterable value.
  *
  * @param value
  */
private[q] class BASV(value: Iterator[?]) extends QTransformable {
  override def transform(transformer: Any => List[Any], binder: QBinder): List[Any] = {
    val questionMarks = value.flatMap(v => transformer(v)) // values are bind, each value produces a question mark
    questionMarks.mkString(",") :: Nil // return a single string with all question marks in list
  }
}

/** Bind Array value
  */
extension (value: (Array[?] | Iterable[?]) | Iterator[?]) {
  @targetName("question")
  def ? : QTransformable = value match {
    case arr: Array[?]   => new BASV(immutable.ArraySeq.unsafeWrapArray(arr).iterator)
    case it: Iterator[?] => new BASV(it)
    case it: Iterable[?] => new BASV(it.iterator)
  }
}
