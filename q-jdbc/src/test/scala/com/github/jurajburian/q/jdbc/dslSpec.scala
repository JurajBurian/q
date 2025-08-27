package com.github.jurajburian.q.jdbc

import com.github.jurajburian.q.*
import munit.FunSuite

class dslSpec extends FunSuite {

  test("q should generate valid Sql instance when safeIn is used ") {

    val a = List.empty[Int]
    val q1 = q"select * from table where ${in("a", a, empty = true)}"

    assertEquals(q1.query, "select * from table where 1=1")
    assertEquals(q1.bindings, List())

    val b = List(1, 2, 3)
    val q2 = q"select * from table where ${in("b", b)}"
    assertEquals(q2.query, s"""select * from table where b in(${b.map(p => "?").mkString(",")})""")
    assertEquals(q2.bindings, b)

  }
}
