package controllers

import java.util.Date
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.inject.Singleton
import models.Trip
import models.TripJsonFormats.tripFormat
import models.User
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsArray
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.Controller
import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.BSONFormats.BSONObjectIDFormat
import play.modules.reactivemongo.json.BSONFormats.BSONRegexFormat
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.Cursor
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.BSONRegex

@Singleton
class Trips extends Controller with MongoController {

  private final val logger: Logger = LoggerFactory.getLogger(classOf[Trips])
  val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd")

  def collection: JSONCollection = db.collection[JSONCollection]("trips")

  //TODO extract authentication in some other place
  def createTrip = (new Users).Authenticated { user =>
    implicit request =>
      val newId = BSONObjectID.generate
      val jsonOpt = request.body.asJson
      if (jsonOpt.isEmpty) Future.successful(BadRequest("invalid json"))
      else jsonOpt.get.validate[Trip] match {
        case s: JsSuccess[Trip] =>
          if (s.get._id.isDefined || s.get.userId.isDefined)
            Future.successful(BadRequest("invalid json"))
          else if (s.get.startDate.after(s.get.endDate)) Future.successful(BadRequest("startDate cannot be after endDate"))
          else {
            collection.insert(s.get.copy(userId = user._id, _id = Option(newId))) map {
              lastError =>
                if (lastError.ok)
                  Created(toNiceTripJson(getTripInternal(user._id.get, newId).get))
                else
                  //logger.debug(s"Successfully inserted with LastError: $lastError")
                  InternalServerError("something went wrong: " + lastError)
            }
          }
        case e: JsError => Future.successful(BadRequest("invalid json"))
      }
  }

  def getTripInternal(userId: BSONObjectID, tripId: BSONObjectID): Option[Trip] = {
    val futureTripOpt = collection.find(Json.obj("userId" -> userId, "_id" -> tripId)).one[Trip]

    //TODO should i make it non-blocking too?
    Await.result(futureTripOpt, Duration(20000, "millis"))
  }

  def toNiceTripJson(trip: Trip): JsObject = {
    def calculateDaysLeft(start: Date) = {
      TimeUnit.DAYS.convert(start.getTime(), TimeUnit.MILLISECONDS) -
        TimeUnit.DAYS.convert(new Date().getTime(), TimeUnit.MILLISECONDS) + 1
    }

    val niceJson = Json.obj("destination" -> trip.destination, "startDate" -> dateFormat.format(trip.startDate),
      "endDate" -> dateFormat.format(trip.endDate), "comment" -> trip.comment,
      "userId" -> trip.userId.get.stringify,
      "id" -> trip._id.get.stringify)

    val daysLeft = calculateDaysLeft(trip.startDate)
    if (daysLeft >= 0) niceJson ++ Json.obj("daysLeft" -> daysLeft)
    else niceJson
  }

  def getTrip(id: String) = (new Users).Authenticated { user =>
    implicit request =>
      collection.find(Json.obj("userId" -> user._id.get, "_id" -> BSONObjectID(id))).one[Trip].map {
        trip =>
          if (trip.isDefined) Ok(toNiceTripJson(trip.get))
          else NotFound("trip not found for this user")
      }
  }

  def updateTrip(id: String) = (new Users).Authenticated { user =>
    implicit request =>
      val jsonOpt = request.body.asJson
      if (jsonOpt.isEmpty) Future.successful(BadRequest("invalid json"))
      else jsonOpt.get.validate[Trip] match {
        case s: JsSuccess[Trip] =>
          if (s.get._id.isDefined || s.get.userId.isDefined)
            Future.successful(BadRequest("invalid json"))
          else if (s.get.startDate.after(s.get.endDate)) Future.successful(BadRequest("startDate cannot be after endDate"))
          else {
            collection.save(s.get.copy(userId = user._id, _id = Some(BSONObjectID(id)))) map {
              error =>
                if (error.ok) {
                  val updatedTripOpt = getTripInternal(user._id.get, BSONObjectID(id))
                  if (updatedTripOpt.isDefined) Ok(toNiceTripJson(updatedTripOpt.get))

                  else InternalServerError("something went wrong retrieving trip")
                } else InternalServerError("something went wrong: " + error)
            }
          }
        case e: JsError => Future.successful(BadRequest("invalid json"))
      }
  }

  def deleteTrip(id: String) = (new Users).Authenticated { user =>
    implicit request =>
      collection.remove(Json.obj("userId" -> user._id, "_id" -> BSONObjectID(id))) map {
        lastError =>
          if (lastError.ok) NoContent
          else InternalServerError("something went wrong: " + lastError)
      }
  }

  def listTrips(dateFrom: Option[String], dateTo: Option[String], destination: Option[String],
    commentContains: Option[String]) = (new Users).Authenticated { user =>

    def buildFilter(user: User, dateFrom: Option[String], dateTo: Option[String],
      destination: Option[String], commentContains: Option[String]): JsObject = {

      def isNotBlank(opt: Option[String]) = {
        opt.isDefined && opt.get != null && !opt.get.trim().isEmpty
      }

      val tripWithUser = Json.obj("userId" -> user._id.get)
      val tripWithDestination =
        if (isNotBlank(destination)) tripWithUser ++ Json.obj("destination" -> BSONRegex("^" + destination.get + "$", "i"))
        else tripWithUser
      val tripWithCommentContaining =
        if (isNotBlank(commentContains)) tripWithDestination ++ Json.obj("comment" -> BSONRegex(commentContains.get, "i"))
        else tripWithDestination
      val tripWithDateFrom =
        if (isNotBlank(dateFrom)) {
          val date = dateFormat.parse(dateFrom.get)
          tripWithCommentContaining ++ Json.obj("endDate" -> Json.obj("$gte" -> date))
        } else tripWithCommentContaining
      val tripWithDateTo =
        if (isNotBlank(dateTo)) {
          val date = dateFormat.parse(dateTo.get)
          tripWithDateFrom ++ Json.obj("startDate" -> Json.obj("$lte" -> date))
        } else tripWithDateFrom
      tripWithDateTo
    }

    implicit request =>
      val filter = buildFilter(user, dateFrom, dateTo, destination, commentContains)
      val cursor: Cursor[Trip] = collection.find(filter).cursor[Trip]
      val futureTripsJsonArray: Future[JsArray] = cursor.collect[List]().map { trips =>
        Json.arr(trips.map(trip => toNiceTripJson(trip)))
      }
      futureTripsJsonArray.map {
        trips =>
          Ok(trips(0))
      }
  }
}
