package com.github.jurajburian.q.jdbc

import com.github.jurajburian.q.*
import java.sql.ResultSet

object SqlRowEncoder extends ProductEncoder[ResultSet]

object SqlRowEncoder2 extends NamedTupleEncoder[ResultSet]
