package io.github.jb.q

import scala.StringContext.*
import scala.collection.mutable

/** Bind value in the Sql
  */
trait QBinder {

  /** Binds value to the Sql
    * @param value
    *   is added to binded collection
    */
  def bind(value: Any): Unit

  /** Binds all values to the Sql
    * @param values
    *   is added to bound collection
    */
  def bindAll(values: Array[Any] | IterableOnce[Any]): Unit
}

trait QTransformable {
  def transform(transformer: Any => List[Any], binder: QBinder): List[Any]
}

/** Sql is container for statement (or fragment of statement) and bindings
  *
  * @param query
  *   resolved query
  * @param bindings
  *   bindings the values need to be bound
  */
case class Q(query: String, bindings: List[Any]) {
  override def toString: String = s"""$query with bindings[${bindings.mkString(",")}]"""
  def stripMargin: Q = copy(query = query.stripMargin)
  def +(s: Q): Q = Q(query + s.query, (bindings ++ s.bindings))
  def +(ss: Iterable[Q]): Q = ss.foldLeft(this)(_ + _)
}

/** sql interpolator return [[Q]]
  */
extension (sc: StringContext) {

  private def binder(mapped: mutable.ListBuffer[Any]) = new QBinder {
    inline def bind(value: Any): Unit = mapped.append(value)

    inline def bindAll(values: Array[Any] | IterableOnce[Any]): Unit = mapped.addAll(
      values match {
        case v: IterableOnce[Any] => v
        case a: Array[Any]        => scala.collection.immutable.ArraySeq.unsafeWrapArray(a)
      }
    )
  }

  /** sql interpolator
    *
    * @param args
    *   interpolation parameters
    * @return
    *   [[Q]] instance with statement and bindings
    */
  def q(args: Any*): Q = {
    val mapped: mutable.ListBuffer[Any] = mutable.ListBuffer.empty[Any]
    val bindable = binder(mapped)

    def transform(p: Any): List[Any] = p match {
      case t: QTransformable =>
        t.transform(transform, bindable)
      case Q(statement, args) =>
        bindable.bindAll(args)
        List(statement)
      case x: Option[_] =>
        bindable.bind(x.orNull)
        List("?")
      case x => // bind value
        bindable.bind(x)
        List("?")
    }

    val transformedArgs = args.flatMap(transform)
    Q(sc.s(transformedArgs*), mapped.toList)
  }
}
