package controllers

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.inject.Singleton
import play.api.mvc.Action
import play.api.mvc.Controller


@Singleton
class Application() extends Controller {

  private final val logger: Logger = LoggerFactory.getLogger(classOf[Application])

  def index = Action {
    logger.info("Serving index page...")
    Ok(views.html.index())
  }
}
