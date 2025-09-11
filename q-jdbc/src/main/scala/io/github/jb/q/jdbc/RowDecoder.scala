package io.github.jb.q.jdbc

import io.github.jb.q.*
import java.sql.ResultSet

/** Decoder for [[java.sql.ResultSet]]
  */
object RowDecoder extends FDecoder[ResultSet]
