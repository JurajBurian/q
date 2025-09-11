package io.github.jb.q

import scala.annotation.StaticAnnotation

/** Annotation to specify alias for a field in a case class.
  * @param name
  *   the name of the alias
  */
case class QAlias(name: String) extends StaticAnnotation
