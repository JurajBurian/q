package com.github.jurajburian.q

import munit.FunSuite

class dslSpec extends FunSuite {

  test("q should generate valid Sql instance when ! is used") {

    val a = 1
    val q = q"select * from table where a = ${a.!}"

    assertEquals(q.query, "select * from table where a = 1")
    assertEquals(q.bindings, List())
  }

  test("q should generate valid Sql instance when ? is used") {

    val a = Array(1, 2, 3)
    val q1 = q"select * from table where a in (${a.?})"

    assertEquals(q1.query, "select * from table where a in (?,?,?)")
    assertEquals(q1.bindings, a.toList)

    val q2 = q"select * from table where a in (${a.toList.?})"

    assertEquals(q2.query, "select * from table where a in (?,?,?)")
    assertEquals(q2.bindings, a.toList)

  }

  test("q should generate valid Sql instance when ? is used with empty list") {

    val a = List.empty[Int]
    val q = q"select * from table where a in (${a.?})"

    assertEquals(q.query, "select * from table where a in ()")
    assertEquals(q.bindings, List())
  }

}
