import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent.Future
import java.text.ParseException

object Global extends GlobalSettings {
 
  // called when a route is found, but it was not possible to bind the request parameters
  override def onBadRequest(request: RequestHeader, error: String) = {
    Future.successful(BadRequest("malformed parameters"))
  } 
 
  // 500 - internal server error
  override def onError(request: RequestHeader, throwable: Throwable) = {
    throwable.getCause() match {
      case p: ParseException => Future.successful(BadRequest("malformed parameters"))
      case i: IllegalArgumentException => Future.successful(BadRequest("malformed parameters"))
      case x => Future.successful(InternalServerError("something went wrong"))
    } 
  }
 
  // 404 - page not found error
  override def onHandlerNotFound(request: RequestHeader) = {
    Future.successful(NotFound("resource not found"))
  }
 
}