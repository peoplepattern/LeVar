package integration

import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import play.api.libs.ws._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import play.api.libs.ws.WSAuthScheme.BASIC
import play.api.libs.json.Json.{ obj => j, toJson }
import levar._
import levar.json._
import levar.Dataset._

class ApiIntegrationTest extends PlaySpec with BeforeAndAfterEach {

  override def beforeEach() {
    db.impl.setUp()
  }

  override def afterEach() {
    db.impl.tearDown()
  }

  "GET /api/ping" must {
    "work" in {
      running(new TestServer(3333, new FakeApplication())) {
        val response = await(WS.url("http://localhost:3333/api/ping").get())
        assert(response.status == 200)
        assert(response.body.trim == "OK")
      }
    }
  }

  def setupTestUser() {
    console.createUser("test-user", "test-pass")
    console.createOrg("test-org", Seq("test-user"))
  }

  "GET /api/test-org/datasets" must {
    "send back a 401 if no credentials provided" in {
      running(new TestServer(3333, new FakeApplication())) {
        val response = await { WS.url("http://localhost:3333/api/test-org/datasets").get() }
        assert(response.status == 401)
      }
    }

    "send back a 404 if the org isn't a thing" in {
      console.createUser("test-user", "test-pass")
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/datasets")
            .withAuth("test-user", "test-pass", BASIC)
            .get()
        }
        assert(response.status == 404)
      }
    }

    "send back empty result set if there's no datasets" in {
      setupTestUser()
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/datasets")
            .withHeaders("Accept" -> "application/json")
            .withAuth("test-user", "test-pass", BASIC)
            .get()
        }
        assert(response.status == 200)
        response.json.asOpt[ResultSet[Dataset]] match {
          case Some(rs) => assert(rs.items.isEmpty)
          case None => fail("did not process JSON: " + response.body)
        }
      }
    }

    "send back a result if there are datasets" in {
      setupTestUser()
      db.impl.createDataset("test-org",
        Dataset("ds1", ClassificationType, DataValidator("text" -> StringField)))
      db.impl.createDataset("test-org",
        Dataset("ds2", RegressionType, DataValidator("text" -> StringField)))
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/datasets")
            .withHeaders("Accept" -> "application/json")
            .withAuth("test-user", "test-pass", BASIC)
            .get()
        }
        assert(response.status == 200)
        response.json.asOpt[ResultSet[Dataset]] match {
          case Some(rs) => {
            assert(rs.items.size == 2)
            assert(rs.items.map(_.id).toSet == Set("ds1", "ds2"))
          }
          case None => fail("did not process JSON: " + response.body)
        }
      }
    }
  }

  "GET /api/test-org/ds1" must {
    "send back a 401 if no credentials provided" in {
      running(new TestServer(3333, new FakeApplication())) {
        val response = await { WS.url("http://localhost:3333/api/test-org/ds1").get() }
        assert(response.status == 401)
      }
    }

    "send back a 404 if the org isn't a thing" in {
      console.createUser("test-user", "test-pass")
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/ds1")
            .withAuth("test-user", "test-pass", BASIC)
            .get()
        }
        assert(response.status == 404)
      }
    }

    "send back a 404 if the dataset isn't a thing" in {
      setupTestUser()
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/ds1")
            .withAuth("test-user", "test-pass", BASIC)
            .get()
        }
        assert(response.status == 404)
      }
    }

    "send back a response when there is one" in {
      setupTestUser()
      db.impl.createDataset("test-org",
        Dataset("ds1", ClassificationType, DataValidator("text" -> StringField)))
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/ds1")
            .withHeaders("Accept" -> "application/json")
            .withAuth("test-user", "test-pass", BASIC)
            .get()
        }
        assert(response.status == 200)
        response.json.asOpt[Dataset] match {
          case Some(ds) => {
            assert(ds.id == "ds1")
            assert(ds.dtype == ClassificationType)
            assert(ds.schema == DataValidator("text" -> StringField))
            assert(ds.createdAt != None)
            assert(ds.updatedAt != None)
          }
          case None => {
            fail("could not parse JSON as result set: " + response.body)
          }
        }
      }
    }
  }

  "POST /api/test-org/datset" must {
    "send back a 401 if no credentials provided" in {
      running(new TestServer(3333, new FakeApplication())) {
        val response = await { WS.url("http://localhost:3333/api/test-org/new_dataset").post("") }
        assert(response.status == 401)
      }
    }

    "send back a 404 if the org isn't a thing" in {
      console.createUser("test-user", "test-pass")
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/new_dataset")
            .withAuth("test-user", "test-pass", BASIC)
            .post("")
        }
        assert(response.status == 404)
      }
    }

    "put a dataset in the DB" in {
      val ds1 = Dataset("ds1", ClassificationType, DataValidator("text" -> StringField))
      setupTestUser()
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/new_dataset")
            .withHeaders("Accept" -> "application/json")
            .withAuth("test-user", "test-pass", BASIC)
            .post(toJson(ds1))
        }
        assert(response.status == 200)
        response.json.asOpt[Dataset] match {
          case Some(ds) => {
            assert(ds.id == "ds1")
            assert(ds.dtype == ClassificationType)
            assert(ds.schema == DataValidator("text" -> StringField))
            assert(ds.createdAt != None)
            assert(ds.updatedAt != None)
          }
          case None => {
            fail("could not parse JSON as result set: " + response.body)
          }
        }
        val ds1t = db.impl.getDataset("test-org", "ds1")
        assert(ds1t.id == ds1.id)
        assert(ds1t.dtype == ds1.dtype)
        assert(ds1t.schema == ds1.schema)
        assert(ds1t.createdAt != None)
        assert(ds1t.updatedAt != None)
      }
    }

    "send a bad response if the dataset schema is incoherent" in {
      val ds1 = j(
        "id" -> "ds1",
        "type" -> "classification",
        "schema" -> j("foo" -> "bar", "redsox" -> "awesome"))
      setupTestUser()
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/new_dataset")
            .withHeaders("Accept" -> "application/json")
            .withAuth("test-user", "test-pass", BASIC)
            .post(ds1)
        }
        assert(response.status == 400)
      }
    }
  }

  "POST /api/test-org/datset/ds1" must {
    "update the ID of a dataset" in {
      setupTestUser()
      db.impl.createDataset("test-org",
        Dataset("ds1", ClassificationType, DataValidator("text" -> StringField)))
      val u1 = Dataset.Update(id = Some("ds2"))
      Thread.sleep(100)
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/ds1/update")
            .withHeaders(
              "Accept" -> "application/json",
              "Content-type" -> "application/json")
            .withAuth("test-user", "test-pass", BASIC)
            .post(toJson(u1))
        }
        assert(response.status == 200)
        response.json.asOpt[Dataset] match {
          case Some(ds) => {
            assert(ds.id == "ds2")
            assert(ds.dtype == ClassificationType)
            assert(ds.schema == DataValidator("text" -> StringField))
            assert(ds.createdAt != None)
            assert(ds.updatedAt != None)
            assert(ds.createdAt != ds.updatedAt)
          }
          case None => fail("could not parse response: " + response.body)
        }
      }
      val ds = db.impl.getDataset("test-org", "ds2")
      assert(ds.id == "ds2")
      assert(ds.dtype == ClassificationType)
      assert(ds.schema == DataValidator("text" -> StringField))
      assert(ds.createdAt != None)
      assert(ds.updatedAt != None)
      assert(ds.createdAt != ds.updatedAt)
    }

    "send back a 400 and do no updates if the ID is taken" in {
      setupTestUser()
      db.impl.createDataset("test-org",
        Dataset("ds1", ClassificationType, DataValidator("text" -> StringField)))
      db.impl.createDataset("test-org",
        Dataset("ds2", RegressionType, DataValidator("text" -> StringField)))
      val u1 = Dataset.Update(id = Some("ds2"))
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/ds1/update")
            .withHeaders(
              "Accept" -> "application/json",
              "Content-type" -> "application/json")
            .withAuth("test-user", "test-pass", BASIC)
            .post(toJson(u1))
        }
        assert(response.status == 400)
      }
    }

    "send back a 404 if the dataset doesn't exist" in {
      setupTestUser()
      db.impl.createDataset("test-org",
        Dataset("ds1", ClassificationType, DataValidator("text" -> StringField)))
      val u1 = Dataset.Update(id = Some("ds2"))
      Thread.sleep(100)
      running(new TestServer(3333, new FakeApplication())) {
        val response = await {
          WS.url("http://localhost:3333/api/test-org/dataset/ds3")
            .withHeaders(
              "Accept" -> "application/json",
              "Content-type" -> "application/json")
            .withAuth("test-user", "test-pass", BASIC)
            .post(toJson(u1))
        }
        assert(response.status == 404)
      }
    }
  }
}
