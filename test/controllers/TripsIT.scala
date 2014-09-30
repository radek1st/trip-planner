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
import play.api.libs.json.JsArray
import reactivemongo.bson.BSONObjectID
import java.util.Date
import java.util.Calendar

@RunWith(classOf[JUnitRunner])
class TripsIT extends Specification {

  val baseUrl = ITHelper.host + "/api/users/me/trips"
  val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd")

  "successfully add, modify, delete and list trips" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    //empty list of trips
    val response = Await.result(WS.url(baseUrl).withHeaders(headers).get(), ITHelper.timeout)
    response.status must equalTo(OK)
    response.json.validate[JsArray].get.value.size must equalTo(0)

    //create a future trip with comment    
    var c: Calendar = Calendar.getInstance()
    c.add(Calendar.DATE, 1)  // +1 day
    val startDate = dateFormat.format(c.getTime()) //+1 day
    c.add(Calendar.DATE, 9)  // +1+9 days
    val endDate = dateFormat.format(c.getTime()) //+10 days
    val comment = "can't wait to see palm trees"
    
    val response1 = Await.result(WS.url(baseUrl).withHeaders(headers).post(Json.obj(
      "destination" -> "aDestination",
      "startDate" -> startDate,
      "endDate" -> endDate,
      "comment" -> comment)), ITHelper.timeout)
    response1.status must equalTo(CREATED)
    response1.json.\("destination").as[String] must equalTo("aDestination")
    response1.json.\("startDate").as[String] must equalTo(startDate)
    response1.json.\("endDate").as[String] must equalTo(endDate)
    response1.json.\("comment").as[String] must equalTo(comment)
    //TODO confirm this is correct - sometimes 1 sometimes 2
    response1.json.\("daysLeft").as[Int] must equalTo(2)
    val userId = response1.json.\("userId").as[String]
    userId must beMatching(ITHelper.idRegex)
    val tripId = response1.json.\("id").as[String]
    tripId must beMatching(ITHelper.idRegex)

    //get the trip
    val response0 = Await.result(WS.url(baseUrl + "/" + tripId).withHeaders(headers).get(), ITHelper.timeout)
    response0.status must equalTo(OK)
    response0.json.\("destination").as[String] must equalTo("aDestination")
    response0.json.\("startDate").as[String] must equalTo(startDate)
    response0.json.\("endDate").as[String] must equalTo(endDate)
    response0.json.\("comment").as[String] must equalTo(comment)
    response0.json.\("userId").as[String] must equalTo(userId)
    response0.json.\("id").as[String] must equalTo(tripId)
    //TODO confirm this is correct - sometimes 1 sometimes 2
    response0.json.\("daysLeft").as[Int] must equalTo(2)
    
    //list one trip
    val response2 = Await.result(WS.url(baseUrl).withHeaders(headers).get(), ITHelper.timeout)
    response2.status must equalTo(OK)
    response2.json.as[JsArray].value should have size 1
    val tripInArray = response2.json.as[JsArray].apply(0)
    tripInArray.\("destination").as[String] must equalTo("aDestination")
    tripInArray.\("startDate").as[String] must equalTo(startDate)
    tripInArray.\("endDate").as[String] must equalTo(endDate)
    tripInArray.\("comment").as[String] must equalTo(comment)
    tripInArray.\("userId").as[String] must equalTo(userId)
    tripInArray.\("id").as[String] must equalTo(tripId)
    //TODO confirm this is correct - sometimes 1 sometimes 2
    response0.json.\("daysLeft").as[Int] must equalTo(2)

    //create another trip, in the past and without the comment
    val response3 = Await.result(WS.url(baseUrl).withHeaders(headers).post(Json.obj(
      "destination" -> "aDestination2",
      "startDate" -> "2014-09-05",
      "endDate" -> "2014-09-07")), ITHelper.timeout)
    response3.status must equalTo(CREATED)
    response3.json.\("destination").as[String] must equalTo("aDestination2")
    response3.json.\("startDate").as[String] must equalTo("2014-09-05")
    response3.json.\("endDate").as[String] must equalTo("2014-09-07")
    response3.json.\("comment").asOpt[String].isDefined must equalTo(false)
    response3.json.\("daysLeft").asOpt[Int].isDefined must equalTo(false)

    val userId2 = response3.json.\("userId").as[String]
    userId2 must beMatching(ITHelper.idRegex)
    val tripId2 = response3.json.\("id").as[String]
    tripId2 must beMatching(ITHelper.idRegex)

    //list two trips
    val response4 = Await.result(WS.url(baseUrl).withHeaders(headers).get(), ITHelper.timeout)
    response4.status must equalTo(OK)
    response4.json.as[JsArray].value should have size 2

    //update second trip
    val response5 = Await.result(WS.url(baseUrl + "/" + tripId2).withHeaders(headers).put(Json.obj(
      "destination" -> "newDestination",
      "startDate" -> "2014-08-05",
      "endDate" -> "2014-08-07",
      "comment" -> "some comment")), ITHelper.timeout)
    response5.status must equalTo(OK)
    response5.json.\("destination").as[String] must equalTo("newDestination")
    response5.json.\("startDate").as[String] must equalTo("2014-08-05")
    response5.json.\("endDate").as[String] must equalTo("2014-08-07")
    response5.json.\("comment").as[String] must equalTo("some comment")
    response5.json.\("userId").as[String] must equalTo(userId2)
    response5.json.\("id").as[String] must equalTo(tripId2)

    //list still two trips
    val response6 = Await.result(WS.url(baseUrl).withHeaders(headers).get(), ITHelper.timeout)
    response6.status must equalTo(OK)
    response6.json.as[JsArray].value should have size 2

    //get trips filtered by destination
    val response11 = Await.result(WS.url(baseUrl + "?destination=newDestination").withHeaders(headers).get(), ITHelper.timeout)
    response11.status must equalTo(OK)
    response11.json.as[JsArray].value should have size 1
    response11.body.toString() must contain("newDestination")

    //get trip filtered by comment
    val response12 = Await.result(WS.url(baseUrl + "?commentContains=palm").withHeaders(headers).get(), ITHelper.timeout)
    response12.status must equalTo(OK)
    response12.json.as[JsArray].value should have size 1
    response12.body.toString() must contain(comment)

    //get trips filtered by date
    val response13 = Await.result(WS.url(baseUrl + "?dateFrom=2014-08-07&dateTo=" + startDate).withHeaders(headers).get(), ITHelper.timeout)
    response13.status must equalTo(OK)
    response13.json.as[JsArray].value should have size 2
    
    val response14 = Await.result(WS.url(baseUrl + "?dateFrom=2014-09-05&dateTo=" + dateFormat.format(new Date())).withHeaders(headers).get(), ITHelper.timeout)
    response14.status must equalTo(OK)
    response14.json.as[JsArray].value should have size 0

    val response15 = Await.result(WS.url(baseUrl + "?dateFrom=2014-08-07&dateTo=2014-08-08").withHeaders(headers).get(), ITHelper.timeout)
    response15.status must equalTo(OK)
    response15.json.as[JsArray].value should have size 1
    
    //delete a trip
    val response7 = Await.result(WS.url(baseUrl + "/" + tripId).withHeaders(headers).delete(), ITHelper.timeout)
    response7.status must equalTo(NO_CONTENT)

    //list only two trips
    val response8 = Await.result(WS.url(baseUrl).withHeaders(headers).get(), ITHelper.timeout)
    response8.status must equalTo(OK)
    response8.json.as[JsArray].value should have size 1

    //delete last trip
    val response9 = Await.result(WS.url(baseUrl + "/" + tripId2).withHeaders(headers).delete(), ITHelper.timeout)
    response9.status must equalTo(NO_CONTENT)

    //list again should be empty
    val response10 = Await.result(WS.url(baseUrl).withHeaders(headers).get(), ITHelper.timeout)
    response10.status must equalTo(OK)
    response10.json.validate[JsArray].get.value.size must equalTo(0)
  }

  //other cases for create trip

  "fail to create a trip with an invalid json" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    val response = Await.result(WS.url(baseUrl).withHeaders(headers).post("{bla}"), ITHelper.timeout)
    response.body.toString must contain("invalid json")
    response.status must equalTo(BAD_REQUEST)
  }

  "fail to create a trip with a missing attribute (destination)" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    val response = Await.result(WS.url(baseUrl).withHeaders(headers).post(Json.obj(
      //"destination" -> "aDestination",
      "startDate" -> "2014-09-04",
      "endDate" -> "2014-09-05",
      "comment" -> "comment")), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("invalid json")
  }

  "fail to create a trip with a malformed (startDate)" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    val response = Await.result(WS.url(baseUrl).withHeaders(headers).post(Json.obj(
      "destination" -> "aDestination",
      "startDate" -> "2014-39-54",
      "endDate" -> "2014-09-05",
      "comment" -> "comment")), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("invalid json")
  }
    
  "fail to create a trip with a startDate after endDate" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    val response = Await.result(WS.url(baseUrl).withHeaders(headers).post(Json.obj(
      "destination" -> "aDestination",
      "startDate" -> "2014-09-06",
      "endDate" -> "2014-09-05",
      "comment" -> "comment")), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("startDate cannot be after endDate")
  }
    
  "fail to create a trip on an invalid url" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    val response = Await.result(WS.url(baseUrl + "/something").withHeaders(headers).post(Json.obj(
      "destination" -> "aDestination",
      "startDate" -> "2014-09-04",
      "endDate" -> "2014-09-05",
      "comment" -> "comment")), ITHelper.timeout)
    response.status must equalTo(NOT_FOUND)
    response.body.toString must contain("resource not found")
  }

  //other cases for update trip

  "fail to update a trip with invalid json" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)
    val tripId = ITHelper.createATrip(headers)

    val response = Await.result(WS.url(baseUrl + "/" + tripId).withHeaders(headers).put("{bla}"), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("invalid json")
  }

  "fail to update a trip with missing property (startDate)" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)
    val tripId = ITHelper.createATrip(headers)

    val response = Await.result(WS.url(baseUrl + "/" + tripId).withHeaders(headers).put(Json.obj(
      "destination" -> "newDestination",
      //"startDate" -> "2014-10-05",
      "endDate" -> "2014-10-07",
      "comment" -> "some comment")), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("invalid json")
  }
  
  "fail to update a trip with malformed property (startDate)" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)
    val tripId = ITHelper.createATrip(headers)

    val response = Await.result(WS.url(baseUrl + "/" + tripId).withHeaders(headers).put(Json.obj(
      "destination" -> "newDestination",
      "startDate" -> "2014-30-55",
      "endDate" -> "2014-10-07",
      "comment" -> "some comment")), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("invalid json")
  }
    
  "fail to update a trip with startDate after endDate" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)
    val tripId = ITHelper.createATrip(headers)

    val response = Await.result(WS.url(baseUrl + "/" + tripId).withHeaders(headers).put(Json.obj(
      "destination" -> "newDestination",
      "startDate" -> "2014-10-08",
      "endDate" -> "2014-10-07",
      "comment" -> "some comment")), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("startDate cannot be after endDate")
  }
    
  "fail to update a trip on an invalid url" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    val response = Await.result(WS.url(baseUrl + "/blabla").withHeaders(headers).put(Json.obj(
      "destination" -> "newDestination",
      "startDate" -> "2014-10-05",
      "endDate" -> "2014-10-07",
      "comment" -> "some comment")), ITHelper.timeout)
    response.status must equalTo(NOT_FOUND)
    response.body.toString must contain("resource not found")
  }

  "update a trip on an nonexisting tripId creates a new trip" in new WithServer {
    val email = ITHelper.randomEmail
    val password = "bla09vlad"
    val userId = ITHelper.createUser(email, password)
    val headers = ITHelper.getAuthHeader(email, password)
    val nonExistingTripId = BSONObjectID.generate.stringify
    val response = Await.result(WS.url(baseUrl + "/" + nonExistingTripId).withHeaders(headers).put(Json.obj(
      "destination" -> "newDestination",
      "startDate" -> "2014-10-05",
      "endDate" -> "2014-10-07",
      "comment" -> "some comment")), ITHelper.timeout)
    response.status must equalTo(OK)
    response.json.\("destination").as[String] must equalTo("newDestination")
    response.json.\("startDate").as[String] must equalTo("2014-10-05")
    response.json.\("endDate").as[String] must equalTo("2014-10-07")
    response.json.\("comment").as[String] must equalTo("some comment")
    response.json.\("userId").as[String] must equalTo(userId)
    response.json.\("id").as[String] must equalTo(nonExistingTripId)
  }

  //other cases for delete trip

  "fail to delete a trip on invalid url" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    val response = Await.result(WS.url(baseUrl + "/blabla").withHeaders(headers).delete(), ITHelper.timeout)
    response.status must equalTo(NOT_FOUND)
    response.body.toString must contain("resource not found")
  }

  "deleting a nonexisting trip should return no content" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    val response = Await.result(WS.url(baseUrl + "/12345678901234567890abcd").withHeaders(headers).delete(), ITHelper.timeout)
    response.status must equalTo(NO_CONTENT)
  }

  //other cases for get trip

  "fail to list trips by providing malformated date" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    val response = Await.result(WS.url(baseUrl + "?dateFrom=2014-08-05&dateTo=" + new Date().getTime()).withHeaders(headers).get(), ITHelper.timeout)
    response.status must equalTo(BAD_REQUEST)
    response.body.toString must contain("malformed parameters")    
  }

  "fail to get a trip on invalid url" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    val response = Await.result(WS.url(baseUrl + "/blabla").withHeaders(headers).get(), ITHelper.timeout)
    response.status must equalTo(NOT_FOUND)
    response.body.toString must contain("resource not found")    
  }

  "fail to get a nonexisting tripId" in new WithServer {
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)

    val response = Await.result(WS.url(baseUrl + "/12345678901234567890abcd").withHeaders(headers).get(), ITHelper.timeout)
    response.status must equalTo(NOT_FOUND)
    response.body.toString must contain("trip not found for this user")    
  }

  "fail to get a trip of other user" in new WithServer {
    val aUserTripId = ITHelper.createATrip(ITHelper.getAuthHeader(ITHelper.createRandomUser))
    
    val headers = ITHelper.getAuthHeader(ITHelper.createRandomUser)
    val response = Await.result(WS.url(baseUrl + "/" + aUserTripId).withHeaders(headers).get(), ITHelper.timeout)
    response.status must equalTo(NOT_FOUND)
    response.body.toString must contain("trip not found for this user")    
  }

}