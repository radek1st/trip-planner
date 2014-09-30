package controllers

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.Helpers.route
import play.api.test.Helpers.running
import play.api.test.Helpers.writeableOf_AnyContentAsJson
import org.specs2.runner.JUnitRunner
import play.api.test.WithServer
import play.api.libs.ws.WS
import play.api.libs.json.JsValue

@RunWith(classOf[JUnitRunner])
class AdminIT extends Specification {

  val baseUrl = ITHelper.host + "/api/users"

      //admin tests
  "admin can list all the users" in new WithServer {
    val response = Await.result(WS.url(baseUrl)
      .withHeaders(ITHelper.getAuthHeader(ITHelper.admin)).get(), ITHelper.timeout)
    response.status must equalTo(OK)
    response.body.toString must contain("\"email\":\"" + ITHelper.admin._1 + "\"")
  }

  "non-admin cannot list all the users" in new WithServer {
    val credentials = ITHelper.createRandomUser

    val response = Await.result(WS.url(baseUrl)
      .withHeaders(ITHelper.getAuthHeader(credentials)).get(), ITHelper.timeout)
    response.status must equalTo(UNAUTHORIZED)
    response.body.toString must contain("unauthorized")
  }
    
  //admin can read any account
  
  "admin can read any account" in new WithServer {
	val email = ITHelper.randomEmail
    val userId = ITHelper.createUser(email, "blaBlaBla")
    
    val response = Await.result(WS.url(baseUrl + "/" + userId)
      .withHeaders(ITHelper.getAuthHeader(ITHelper.admin)).get(), ITHelper.timeout)
    response.status must equalTo(OK)
    response.body.toString must contain(email)
  }

  "non-admin cannot read accounts of others" in new WithServer {
    val email = ITHelper.randomEmail
    val userId = ITHelper.createUser(email, "blaBlaBla")
    
    val otherCredentials = ITHelper.createRandomUser   

    val response = Await.result(WS.url(baseUrl + "/" + userId)
      .withHeaders(ITHelper.getAuthHeader(otherCredentials)).get(), ITHelper.timeout)
    response.status must equalTo(UNAUTHORIZED)
    response.body.toString must contain("unauthorized")
  }
  
  
  //admin can modify any
  //admin can delete anyone
  
  //read, list, edit, delete others trips
}