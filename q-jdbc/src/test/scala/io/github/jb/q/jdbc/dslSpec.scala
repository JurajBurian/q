package io.github.jb.q.jdbc

import io.github.jb.q.*
import munit.FunSuite

import java.sql.Date
import java.time.ZoneOffset

class dslSpec extends FunSuite {

  test("q should generate valid Q instance when safeIn is used") {

    val a = List.empty[Int]
    val q1 = q"select * from table where ${in("a", a, empty = true)}"

    assertEquals(q1.query, "select * from table where 1=1")
    assertEquals(q1.bindings, List())

    val b = List(1, 2, 3)
    val q2 = q"select * from table where ${in("b", b)}"
    assertEquals(q2.query, s"""select * from table where b in(${b.map(p => "?").mkString(",")})""")
    assertEquals(q2.bindings, b)

  }

  test("q should generate valid Q instance when inOrTrue or inOrFalse is used") {
    val a = List.empty[Int]
    val q1t = q"select * from table where ${"a".inOrTrue(a)}"
    assertEquals(q1t.query, "select * from table where 1=1")
    assertEquals(q1t.bindings, List())
    val q1f = q"select * from table where ${"a".inOrFalse(a)}"
    assertEquals(q1f.query, "select * from table where 1=0")
    assertEquals(q1f.bindings, List())

    val b = List(1, 2, 3)
    val q2 = q"select * from table where ${"b".inOrTrue(b)}"
    assertEquals(q2.query, s"""select * from table where b in(${b.map(p => "?").mkString(",")})""")
    assertEquals(q2.bindings, b)
  }

  test("q should generate valid Q instance when at is used") {
    val timezone = ZoneOffset.UTC
    val q = q"select t ${timezone.at}"
    assertEquals(q.query, "select t at time zone ?")
    assertEquals(q.bindings, List("Z"))
  }

  test("q should generate valid Q instance when (*) is used") {
    case class User(id: Long, name: String, age: Date)
    val q1 = q"insert (${attrProjection[User](Set("id"))})"
    assertEquals(q1.query, "insert (name,age)")
    type User2 = (id: Long, name: String, age: Date)
    val q2 = q"insert (${attrProjection[User2]()})"
    assertEquals(q2.query, "insert (id,name,age)")
  }

  test("q should generate valid Q instance when (*) is used on Iterator") {
    case class User(name: String, age: Int)
    val seq1 = List(User("n", 1), User("n", 2))
    val q = q"values ${seq1.??}"
    assertEquals(q.query, "values (?,?),(?,?)")
    assertEquals(q.bindings, seq1.flatMap(p => List(p.name, p.age)))

    type User2 = (name: String, age: Int)
    val seq2 = List(("n", 1), ("n", 2))
    val q2 = q"values ${seq2.??}"
    assertEquals(q.query, "values (?,?),(?,?)")
    assertEquals(q.bindings, seq1.flatMap(p => List(p.name, p.age)))

  }

}
