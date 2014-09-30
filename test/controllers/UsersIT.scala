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
class UsersIT extends Specification {

  val baseUrl = ITHelper.host + "/api/users"
    
  "create a user from valid json" in new WithServer {
    val email = ITHelper.randomEmail
    val response = Await.result(WS.url(baseUrl).post(Json.obj(
      "email" -> email,
      "password" -> "somePassword")), ITHelper.timeout)
    response.status must equalTo(CREATED)
    response.json.\("email").as[String] must equalTo(email)
    response.json.\("password").asOpt[String] must equalTo(None)
    response.json.\("id").as[String] must beMatching(ITHelper.idRegex)
  }

  "fail to create a user with existing email" in new WithServer {
    val email = ITHelper.randomEmail
    ITHelper.createUser(email, "London")
    val response = Await.result(WS.url(baseUrl).post(Json.obj(
      "email" -> email,
      "password" -> "London")), ITHelper.timeout)
    response.status must equalTo(CONFLICT)
    response.body.toString must contain("user already exists!")
  }

  "fail to create a user with a weak password" in new WithServer {
    val response = Await.result(WS.url(baseUrl).post(Json.obj(
      "email" -> ITHelper.randomEmail,
      "password" -> "abc")), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("weak password")
  }

  "fail to create a user with invalid email" in new WithServer {
    val response = Await.result(WS.url(baseUrl).post(Json.obj(
      "email" -> "invalid-email",
      "password" -> "London")), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("invalid email")
  }

  "fail to create a user with invalid json" in new WithServer {
    val response = Await.result(WS.url(baseUrl).post("{asda,}"), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("malformed parameters")
  }

  "fail to create a user with invalid json (missing user)" in new WithServer {
    val response = Await.result(WS.url(baseUrl).post(Json.obj(
      "password" -> "London")), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("invalid json")
  }

  "get user details with valid credentials" in new WithServer {
    val credentials = ITHelper.createRandomUser
    val response = Await.result(WS.url(baseUrl + "/me")
      .withHeaders(ITHelper.getAuthHeader(credentials)).get(), ITHelper.timeout)
    response.status must equalTo(OK)
    response.json.\("email").toString must contain(credentials._1)
  }

  "fail auth without the auth header" in new WithServer {
    val response = Await.result(WS.url(baseUrl + "/me").get(), ITHelper.timeout)
    response.status must equalTo(UNAUTHORIZED)
    response.body.toString must contain("unauthorized")
  }

  "fail auth with invalid binary encoding" in new WithServer {
    val response = Await.result(WS.url(baseUrl + "/me")
      .withHeaders(("Authorization", "blablabla")).get(), ITHelper.timeout)
    response.status must equalTo(UNAUTHORIZED)
    response.body.toString must contain("unauthorized")
  }

  "fail auth with invalid username" in new WithServer {
    val credentials = ITHelper.createRandomUser
    val response = Await.result(WS.url(baseUrl + "/me")
      .withHeaders(ITHelper.getAuthHeader("invalid@sony.com", credentials._2)).get(), ITHelper.timeout)
    response.status must equalTo(UNAUTHORIZED)
    response.body.toString must contain("unauthorized")
  }

  "fail auth with invalid password" in new WithServer {
    val credentials = ITHelper.createRandomUser
    val response = Await.result(WS.url(baseUrl + "/me")
      .withHeaders(ITHelper.getAuthHeader(credentials._1, "invalid password")).get(), ITHelper.timeout)
    response.status must equalTo(UNAUTHORIZED)
    response.body.toString must contain("unauthorized")
  }

  //NICE TO HAVE:

  "update user password" in new WithServer {
    val credentials = ITHelper.createRandomUser
    val newPassword = "someNewPassword"

    val response = Await.result(WS.url(baseUrl + "/me")
      .withHeaders(ITHelper.getAuthHeader(credentials)).put(Json.obj(
        "email" -> credentials._1,
        "password" -> newPassword)), ITHelper.timeout)

    response.status must equalTo(OK)
    response.json.\("email").as[String] must equalTo(credentials._1)
    response.json.\("password").asOpt[String] must equalTo(None)
    response.json.\("id").as[String] must beMatching(ITHelper.idRegex)

    //check that old password doesnt work
    val response1 = Await.result(WS.url(baseUrl + "/me")
      .withHeaders(ITHelper.getAuthHeader(credentials)).get(), ITHelper.timeout)
    response1.status must equalTo(UNAUTHORIZED)
    response1.body.toString must contain("unauthorized")

    //and the new one does
    val response2 = Await.result(WS.url(baseUrl + "/me")
      .withHeaders(ITHelper.getAuthHeader(credentials._1, newPassword)).get(), ITHelper.timeout)
    response2.status must equalTo(OK)
  }

  "fail to update the user with weak password" in new WithServer {
    val credentials = ITHelper.createRandomUser
    val newPassword = "short"

    val response = Await.result(WS.url(baseUrl + "/me")
      .withHeaders(ITHelper.getAuthHeader(credentials)).put(Json.obj(
        "email" -> credentials._1,
        "password" -> newPassword)), ITHelper.timeout)

    response.body.toString must contain("weak password")
    response.status must equalTo(BAD_REQUEST)
  }

  "users can successfully delete their account" in new WithServer {
    val credentials = ITHelper.createRandomUser

    val response = Await.result(WS.url(baseUrl + "/me")
      .withHeaders(ITHelper.getAuthHeader(credentials)).delete(), ITHelper.timeout)

    response.status must equalTo(NO_CONTENT)

    //and cannot auth anymore
    val response1 = Await.result(WS.url(baseUrl + "/me")
      .withHeaders(ITHelper.getAuthHeader(credentials)).get(), ITHelper.timeout)
    response1.status must equalTo(UNAUTHORIZED)
    response1.body.toString must contain("unauthorized")

  }

}