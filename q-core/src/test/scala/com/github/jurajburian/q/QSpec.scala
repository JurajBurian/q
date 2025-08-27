package com.github.jurajburian.q

import munit.FunSuite

class QSpec extends FunSuite {

  test("sql should generate valid  Sql instance") {

    val a = 1
    val sql = q"select * from table where a = $a"

    assertEquals(sql.query, "select * from table where a = ?")
    assertEquals(sql.bindings, List(a))
  }

  test("sql should generate valid Sql instance with multiple values") {

    val a = 1
    val b = "b"
    val sql = q"select * from table where a = $a and b = $b"

    assertEquals(sql.query, "select * from table where a = ? and b = ?")
    assertEquals(sql.bindings, List(a, b))
  }

  test("sql should generate valid Sql when combine two Sql") {

    val a = 1
    val b = "b"
    val sql1 = q"select * from table where a = $a"
    val sql = q"$sql1 and b = $b"

    assertEquals(sql.query, "select * from table where a = ? and b = ?")
    assertEquals(sql.bindings, List(a, b))
  }  
  
}
