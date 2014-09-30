package controllers

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import play.api.libs.ws.WS
import play.api.libs.json.Json
import play.api.test.Helpers._
import org.specs2.Specification

object ITHelper {

  val host = "http://trip-it.herokuapp.com"
  //val host = "http://localhost:9000"
    
  val timeout: FiniteDuration = FiniteDuration(10, TimeUnit.SECONDS)
  val idRegex = "[a-fA-F0-9]{24}".r
  def randomEmail = UUID.randomUUID().toString().replaceAll("-", "") + "@wp.pl"

  val admin = ("admin@tripplanner.com","superSecret")
  
  def createUser(email: String, password: String): String = {
    val response = Await.result(WS.url(host + "/api/users").post(Json.obj(
      "email" -> email,
      "password" -> password)), timeout)
    if (response.status != 201) throw new IllegalStateException("failed to create the user")
    else response.json.\("id").as[String]
  }

  def createRandomUser(): (String, String) = {
    val email = randomEmail
    val password = "LondonIsCalling"
    createUser(email, password)
    (email, password)
  }

  def getAuthHeader(credentials: (String, String)): (String, String) = {
    val authHeader = "Basic " + new sun.misc.BASE64Encoder().encodeBuffer(
      (credentials._1 + ":" + credentials._2).getBytes()).trim()
    ("Authorization", authHeader)
  }
  
  def createATrip(headers: (String, String)): String = {
    val response = Await.result(WS.url(host + "/api/users/me/trips").withHeaders(headers).post(Json.obj(
      "destination" -> "aDestination",
      "startDate" -> "2014-09-04",
      "endDate" -> "2014-09-05",
      "comment" -> "comment")), ITHelper.timeout)
    if (response.status != 201) throw new IllegalStateException("failed to create the trip")
    response.json.\("id").as[String]
  }
}