package io.github.jb.q.cassandra

import io.github.jb.q.*
import com.datastax.oss.driver.api.core.CqlSession
import munit.FunSuite
import org.testcontainers.cassandra.CassandraContainer

class DataSourceSpec extends FunSuite {

  lazy val dbContainer: CassandraContainer = {
    val c = new CassandraContainer("cassandra:5.0")
    c.start()
    c
  }
  override def afterAll(): Unit = {
    dbContainer.stop()
  }

  def ds = {
    val sb = CqlSession.builder().addContactPoint(dbContainer.getContactPoint).withLocalDatacenter("datacenter1")
    CassandraDatasource(sb)
  }

  test("initial test") {

    case class Res(in: Int)

    given RowDecoder.TypedDecoder[Res] = RowDecoder.derive()

    ds[Res](
      q"""CREATE KEYSPACE IF NOT EXISTS test WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }""".stripMargin
    )

    ds[Res](
      q"""CREATE TABLE IF NOT EXISTS test.test_table (id int primary key, name text)""".stripMargin
    )

    ds[Res](q"""INSERT INTO test.test_table (id, name) VALUES (1, 'test')""".stripMargin)

    type Data = (id: Int, name: String)

    given RowDecoder.TypedDecoder[Data] = RowDecoder.derive()

    val res = ds[Data](q"""SELECT * FROM test.test_table""".stripMargin)

    println(res)
    assert(res.nonEmpty)

  }

}
