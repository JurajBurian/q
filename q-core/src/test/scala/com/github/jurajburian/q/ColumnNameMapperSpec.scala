package com.github.jurajburian.q

import munit.FunSuite

class ColumnNameMapperSpec extends FunSuite {

  test("camelCase to snake_case should work") {
    assertEquals(ColumnNameMapper.camelToSnake("camelCase"), "camel_case")
    assertEquals(ColumnNameMapper.camelToSnake("CamelCase"), "camel_case")
    assertEquals(ColumnNameMapper.camelToSnake("camel"), "camel")
    assertEquals(ColumnNameMapper.camelToSnake("Camel"), "camel")
    assertEquals(ColumnNameMapper.camelToSnake("CAMEL"), "c_a_m_e_l")
    assertEquals(ColumnNameMapper.camelToSnake("camelCASETest"), "camel_c_a_s_e_test")
    assertEquals(ColumnNameMapper.camelToSnake("CamelCASETest"), "camel_c_a_s_e_test")
  }
}
