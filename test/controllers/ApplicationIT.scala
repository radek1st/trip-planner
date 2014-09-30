package controllers

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Await
import java.util.concurrent.TimeUnit
import play.api.libs.ws.WS
import play.api.libs.json.Json
import org.specs2.runner.JUnitRunner
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class ApplicationIT extends Specification {

  val timeout: FiniteDuration = FiniteDuration(10, TimeUnit.SECONDS)
  val baseUrl = ITHelper.host

  "render the index page correctly" in new WithServer {
    val response = Await.result(WS.url(baseUrl).get, timeout)
    response.status must equalTo(OK)
    response.header("content-type").get must equalTo("text/html; charset=utf-8")
    response.body must contain("<html data-ng-app=\"tripPlannerApp\">")
  }
  
  "send 404 on page not found" in new WithServer {
    val response = Await.result(WS.url(baseUrl + "/boom").get, timeout)
    response.status must equalTo(NOT_FOUND)

    val response1 = Await.result(WS.url(baseUrl + "/api").get, timeout)
    response1.status must equalTo(NOT_FOUND)

    val response2 = Await.result(WS.url(baseUrl + "/api/account").get, timeout)
    response2.status must equalTo(NOT_FOUND)
  }

// would be nice, but can't seem to get it working
//  "run in a browser" in new WithBrowser {
//  browser.goTo(baseUrl)
//  browser.$("#h3").getTexts().get(0) must equalTo("Welcome to Trip Planner")
//    
//  browser.goTo(baseUrl + "#trips")
//  browser.$("#h3").getTexts().get(0) must equalTo("Welcome to Trip Planner")
//    
//  browser.url must equalTo("/")
//  browser.$("#title").getTexts().get(0) must equalTo("Hello Coco")
//}
  
}