package io.github.jb.q.cassandra

import com.datastax.oss.driver.api.core.cql.Row
import io.github.jb.q.FDecoder

object RowDecoder extends FDecoder[Row]
