package controllers

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.inject.Singleton
import models.User
import models.Role
import models.UserJsonFormats.userFormat
import services.HashingService
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsArray
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.Controller
import play.api.mvc.Request
import play.api.mvc.SimpleResult
import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.BSONFormats.BSONObjectIDFormat
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType
import reactivemongo.core.errors.DatabaseException

@Singleton
class Users extends Controller with MongoController {

  private val logger: Logger = LoggerFactory.getLogger(classOf[Users])

  def collection: JSONCollection = db.collection[JSONCollection]("accounts")
  
  def createUser = Action.async(parse.json) {
    def isEmailValid(email: String): Boolean = {
        val emailRegex = """(\w+)@([\w\.]+)""".r
        emailRegex.unapplySeq(email).isDefined
    }

    request =>
      request.body.validate[User].map {
        user =>
          if (user._id.isDefined || user.role.isDefined) Future.successful(BadRequest("invalid json"))
          else if (!isPasswordStrongish(user.password)) Future.successful(BadRequest("weak password"))
          else if (!isEmailValid(user.email)) Future.successful(BadRequest("invalid email"))
          else {
              collection.insert(user.copy(role = Some(Role.user.toString()),
                  password = HashingService.hashPassword(user.password))).map {
                _ => Created(toNiceUserJson(getUserInternal(Json.obj("email" -> user.email))))
              }.recover {
                case e: DatabaseException if (e.code.get == 11000) => Conflict("user already exists!")
                case e => InternalServerError("something went wrong: " + e.getMessage())
              }
          }
      }.getOrElse(Future.successful(BadRequest("invalid json")))
  }

  //restricted to administrator only
  def listUsers = Authenticated { user =>
    implicit request =>
    if(!Role.isAdmin(user.role.get)) Future.successful(Unauthorized("unauthorized"))
    else {
    val cursor: Cursor[User] = collection.find(Json.obj()).cursor[User]
    val futureUsersList: Future[List[User]] = cursor.collect[List]()
    val futurePersonsJsonArray: Future[JsArray] = futureUsersList.map { users =>
      Json.arr(users)
    }
    futurePersonsJsonArray.map {
      users =>
        Ok(users(0))
    }
    }
  }

  def issueToken = Action.async(parse.json) {
    def encodeCredential(user: User): String = {
      val toEncode = user.email + ":" + user.password
      new sun.misc.BASE64Encoder().encodeBuffer(toEncode.getBytes()).trim();
    }

    request =>
      val userOption = request.body.validate[User].asOpt
      if (userOption.isDefined) Future.successful(
        Created(Json.obj("accessToken" -> encodeCredential(userOption.get))))
      else Future.successful(BadRequest("malformed json: " + request.body))
  }

  def getUser = Authenticated { user =>
    implicit request =>
      collection.find(Json.obj("_id" -> user._id.get)).one[User].map {
        user =>
          if (user.isDefined) Ok(toNiceUserJson(user))
          else NotFound("user '" + user.get._id.get.stringify + "' doesn't exist")
      }
      Future.successful(Ok(toNiceUserJson(getUserInternal(Json.obj("_id" -> user._id.get)))))
  }
  
  def getUserById(userId: String) = Authenticated { user =>
    implicit request =>
      if(user._id.get.stringify.equalsIgnoreCase(userId) || Role.isAdmin(user.role.get)) {
        //ok, do the normal get
        Future.successful(Ok("welcome admin"))
      } else Future.successful(Unauthorized("unauthorized"))
  }

  def updateUser = Authenticated { user =>
    implicit request =>
      request.body.asJson.get.validate[User] match {
        case s: JsSuccess[User] =>
          if (s.get._id.isDefined || s.get.role.isDefined)
            Future.successful(BadRequest("invalid json"))
          else if(user.email != s.get.email) Future.successful(BadRequest("invalid email"))
          else if(!isPasswordStrongish(s.get.password)) Future.successful(BadRequest("weak password"))
          else {
            collection.update(Json.obj("_id" -> user._id.get),
              s.get.copy(role = user.role, 
                  password = HashingService.hashPassword(s.get.password))) map {
                error =>
                  if (error.ok)
                    Ok(toNiceUserJson(getUserInternal(Json.obj("_id" -> user._id.get))))
                  else InternalServerError("something went wrong: " + error)
              }
          }
        case e: JsError => Future.successful(BadRequest("invalid json"))
      }
  }
  
  def deleteUser = Authenticated { user =>
    implicit request =>
      collection.remove(Json.obj("_id" -> user._id)) map {
        lastError =>
          if (lastError.ok) NoContent
          else InternalServerError("something went wrong deleting the user: " + lastError)
      }
  }

  def toNiceUserJson(userOpt: Option[User]): JsObject = {
    if (userOpt.isDefined)
      Json.obj("email" -> userOpt.get.email, "id" -> userOpt.get._id.get.stringify)
    else Json.obj("error" -> "true")
  }

  def getUserInternal(jsonObj: JsObject): Option[User] = {
    val futureUserOpt = collection.find(jsonObj).one[User]
    //TODO should i make it non-blocking too?
    Await.result(futureUserOpt, Duration(10000, "millis"))
  }
  
  def isPasswordStrongish(password: String): Boolean = {
	  val passwordRegex = "^\\w{6}\\w*$".r
	  passwordRegex.findFirstMatchIn(password).isDefined
  }

  def Authenticated(f: User => Request[AnyContent] => Future[SimpleResult]) = {
    Action.async {
      request =>
        val denied = Future.successful(Unauthorized("unauthorized"))
        val authorization = "authorization";
        val authHeader = request.headers.get(authorization)

        if (authHeader.isEmpty) denied
        else {
          val auth = authHeader.get.substring(6);
          val decodedAuth = new sun.misc.BASE64Decoder().decodeBuffer(auth);
          val credString = new String(decodedAuth, "UTF-8").split(":");

          if (credString == null || credString.length != 2) denied
          else {
            val username = credString(0);
            val password = credString(1);

            val userOpt = getUserInternal(Json.obj("email" -> username))

            if (userOpt.isDefined) {
              val user = userOpt.get
              if (HashingService.isMatch(password, user.password)) f(user)(request)
              else denied
            } else denied
          }
        }
    }
  }
}
