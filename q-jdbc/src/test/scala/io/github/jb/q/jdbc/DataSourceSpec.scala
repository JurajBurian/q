package io.github.jb.q.jdbc

import io.github.jb.q.*
import io.github.jb.q.jdbc.*
import com.zaxxer.hikari
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import munit.FunSuite

import java.time.{Instant, LocalDate}
import scala.util.{Random, Try}

class DataSourceSpec extends FunSuite {

  lazy val dbContainer: PostgreSQLContainer[Nothing] = {
    val din = DockerImageName.parse("postgresai/extended-postgres").asCompatibleSubstituteFor("postgres")
    val pg = new PostgreSQLContainer(din.withTag("17"))
    pg.setCommand("postgres -c shared_preload_libraries=timescaledb,pg_hint_plan,pg_stat_statements")
    pg.start()
    pg
  }

  val ds: HikariDataSource = {
    val url =
      s"jdbc:postgresql://${dbContainer.getHost}:${dbContainer.getFirstMappedPort}/${dbContainer.getDatabaseName}"
    val cfg = new hikari.HikariConfig()
    cfg.setJdbcUrl(url)
    cfg.setUsername(dbContainer.getUsername)
    cfg.setPassword(dbContainer.getPassword)
    cfg.setDriverClassName("org.postgresql.Driver")
    HikariDataSource(cfg)
  }

  override def afterAll(): Unit = {
    dbContainer.stop()
    ds.close()
  }

  { // first block of tests

    def testTableData = List("test1", "test2", "test3")

    def createTestTables(): Int = {
      ds.write.update(
        q"""create table if not exists test_table (id serial primary key, name varchar(100) not null)"""
      )
    }

    def insertTestTable(): Int = {
      ds.write.update(q"""insert into test_table (name) values
           |${testTableData.map(p => s"('$p')").mkString(",\n").!}""".stripMargin)
    }

    def dropTestTable(): Int = {
      ds.write.update(
        q"drop table if exists test_table"
      )
    }

    test("selects mapped to case classes should return valid values") {

      assertEquals(createTestTables(), 0)
      assertEquals(insertTestTable(), 3)

      case class Count(count: Int)

      given SqlRowDecoder.TypedDecoder[Count] = SqlRowDecoder.derive()

      case class TestRow(id: Int, name: String)

      given SqlRowDecoder.TypedDecoder[TestRow] = SqlRowDecoder.derive()

      val res = ds.read[Count](q"select count(*) as count from test_table")
      val count = res.headOption
      assertEquals(count, Some(Count(3)))

      val rows = ds.read[TestRow](q"select id, name from test_table order by id")
      val expectedRows = testTableData.zipWithIndex.map { case (name, idx) =>
        TestRow(idx + 1, name)
      }
      assertEquals(rows, expectedRows)

      assertEquals(dropTestTable(), 0)
    }

    test("selects mapped to Named tuples should return valid values") {

      assertEquals(createTestTables(), 0)
      assertEquals(insertTestTable(), 3)

      type Count = (count: Int)

      given SqlRowDecoder.TypedDecoder[Count] = SqlRowDecoder.derive()

      type TestRow = (id: Int, name: String)

      given SqlRowDecoder.TypedDecoder[TestRow] = SqlRowDecoder.derive()

      // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      // Warning: when using Named tuple with single argument, the type must be specified explicitly on the right side
      // otherwise the compiler fails with:
      // [error] -- [E124] Cyclic Error: /home/jubu/work/q/q-jdbc/src/test/scala/com/github/jurajburian/q/jdbc/DataSourceSpec.scala:93:28
      // [error] 93 |    val res = ds.read[Count](q"select count(*) as count from test_table")
      // [error]    |                            ^
      // [error]    |given instance given_TypedDecoder_Count needs result type because its right-hand side attempts implicit search
      // [error]    |
      // [error]    | Run with -explain-cyclic for more details.

      val res: Iterable[Count] = ds.read(q"select count(*) as count from test_table")
      val count = res.headOption
      assertEquals(count, Some((count = 3)))

      val rows = ds.read[TestRow](q"select id, name from test_table order by id")
      val expectedRows = testTableData.zipWithIndex.map { case (name, idx) => (idx + 1, name) }
      assertEquals(rows, expectedRows)

      assertEquals(dropTestTable(), 0)
    }
  }

  { // second block of tests

    given ColumnNameMapper = ColumnNameMapper.camelToSnake

    def createTestTablesWithData[T](projection: NBV)(using
        decoder: SqlRowDecoder.TypedDecoder[T]
    ): Iterable[T] = {
      ds.write.update(
        q"""
           |CREATE TABLE customers (
           |    customer_id INT PRIMARY KEY,
           |    customer_name VARCHAR(100) NOT NULL,
           |    city VARCHAR(50),
           |    email VARCHAR(100)
           |);
           |CREATE TABLE orders (
           |    order_id SERIAL PRIMARY KEY,
           |    customer_id INT,
           |    order_date DATE NOT NULL,
           |    amount DECIMAL(10, 2) NOT NULL,
           |    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
           |);
           |INSERT INTO customers (customer_id, customer_name, city, email) VALUES
           |(1, 'John Smith', 'New York', 'john@email.com'),
           |(2, 'Jane Doe', 'Boston', 'jane@email.com'),
           |(3, 'Bob Johnson', 'Chicago', 'bob@email.com'),
           |(4, 'Alice Brown', 'New York', 'alice@email.com');
         """.stripMargin
      )

      val bd = LocalDate.parse("2025-01-01")

      type InsertOrder = (customerId: Int, orderDate: java.time.LocalDate, amount: BigDecimal)

      val orders: List[InsertOrder] = List(
        (1, bd, 150.00),
        (1, bd.plusDays(1), 75.50),
        (2, bd.plusDays(2), 200.00),
        (3, bd.plusDays(4), 50.00),
        (1, bd.plusDays(5), 125.00)
      )

      ds.write[T](q"INSERT INTO orders ($projection) VALUES ${orders.??} returning *")
    }

    def dropTestTables(): Int = {
      ds.write.update(
        q"drop table if exists orders; drop table if exists customers"
      )
    }

    test("joined select mapped to case classes should return valid values") {

      type Customer = (customerId: Int, customerName: String, city: String, email: String)
      type Order = (orderId: Int, customerId: Int, orderDate: java.sql.Date, amount: BigDecimal)

      given SqlRowDecoder.TypedDecoder[Customer] = SqlRowDecoder.derive[Customer]()

      given SqlRowDecoder.TypedDecoder[Order] = SqlRowDecoder.derive[Order]()

      val orders = createTestTablesWithData[Order](attrProjection[Order](Set("orderId")))

      val rows1: Iterable[(Customer, Order)] = ds.read(
        q"""
           |SELECT c.*, o.* FROM customers c
           |INNER JOIN orders o ON c.customer_id = o.customer_id
           |WHERE c.customer_id IN(${List(1, 2, 3, 4).?})""".stripMargin
      )

      val rows2: Iterable[Order] = ds.read(q"SELECT * FROM orders WHERE customer_id = ${1}")

      assertEquals(rows2, orders.filter(_.customerId == 1))

      // group by customer
      // create map of customer to list of orders
      val res: Map[Customer, Iterable[Order]] = rows1.groupBy(_._1).view.mapValues(_.map(_._2)).toMap

      // we have 3 customers with orders
      assertEquals(res.size, 3)

      // customer 1 has 3 orders
      assertEquals(res.find(_._1.customerId == 1).get._2.toList, rows2)

      assertEquals(dropTestTables(), 0)
    }

    test("joined select mapped to Named tuples should return valid values") {

      case class Customer(customerId: Int, customerName: String, city: String, email: String)
      case class Order(orderId: Int, customerId: Int, orderDate: java.sql.Date, amount: BigDecimal)

      given SqlRowDecoder.TypedDecoder[Customer] = SqlRowDecoder.derive[Customer]()
      given SqlRowDecoder.TypedDecoder[Order] = SqlRowDecoder.derive[Order]()

      val orders = createTestTablesWithData[Order](attrProjection[Order](Set("orderId")))

      val rows1: Iterable[(Customer, Order)] = ds.read(
        q"""
         |SELECT c.*, o.* FROM customers c
         |INNER JOIN orders o ON c.customer_id = o.customer_id
         |WHERE ${"c.customer_id".inOrFalse(List(1, 2, 3, 4))}""".stripMargin
      )

      val rows2: Iterable[Order] = ds.read(q"SELECT * FROM orders WHERE customer_id = ${1}")

      assertEquals(rows2, orders.filter(_.customerId == 1))

      // group by customer
      // create map of customer to list of orders
      val res: Map[Customer, Iterable[Order]] = rows1.groupBy(_._1).view.mapValues(_.map(_._2)).toMap

      // we have 3 customers with orders
      assertEquals(res.size, 3)

      // customer 1 has 3 orders
      assertEquals(res.find(_._1.customerId == 1).get._2.toList, rows2)

      assertEquals(dropTestTables(), 0)
    }
  }

  { // third block of tests

    case class TestData(id: Int, name: String, date: LocalDate, score: BigDecimal)

    given SqlRowDecoder.TypedDecoder[TestData] = SqlRowDecoder.derive()

    val random = new Random()
    val names = Array("A", "B", "C", "D")

    val testData: List[TestData] = (1 to 100).map { i =>
      TestData(i, names(i % 4), LocalDate.now().minusDays(i), BigDecimal.decimal(random.nextDouble()))
    }.toList

    def createTable(): Int = {
      ds.write.update(
        q"""
           |CREATE TABLE test_table (
           |    id INT PRIMARY KEY,
           |    name VARCHAR(100) NOT NULL,
           |    date DATE NOT NULL,
           |    score NUMERIC NOT NULL
           |);
         """.stripMargin
      )
    }

    def insertData(): CloseableIterator[TestData] = {
      ds.write.manual[TestData](
        q"INSERT INTO test_table (${attrProjection[TestData]()}) VALUES ${testData.??} returning *"
      )
    }

    def dropTable() = ds.write.update(q"drop table if exists test_table")

    test(
      "inserting data using manual should return valid values and close connection when manual after iteration of all elements"
    ) {
      assertEquals(createTable(), 0)
      val it = insertData()
      assertEquals(it.isClosed, false)
      val data = it.toList
      assertEquals(it.isClosed, true)
      assertEquals(data, testData)
      assertEquals(dropTable(), 0)
    }

    test("during insert data using manual - when iterator is closed, read data should fail") {
      assertEquals(createTable(), 0)
      val it = insertData()
      it.close()
      assertEquals(it.isClosed, true)
      val res = Try(it.toList)
      assertEquals(res.isFailure, true)
      assertEquals(dropTable(), 0)
    }

    test("insert, select in one transaction for unmanaged should pass") {
      assertEquals(createTable(), 0)
      val wds = ds.write.unmanaged
      try {
        val res1 = wds[TestData](
          q"INSERT INTO test_table (${attrProjection[TestData]()}) VALUES ${testData.??} returning *"
        )
        val res2 = wds[TestData](q"SELECT * from test_table")
        assertEquals(res1, res2)
      } finally { wds.close() }
    }

  }
}
