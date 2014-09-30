package services

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HashingServiceUT extends Specification { //with Mockito {

  val password = "superSecret"
  val passwordHash = HashingService.hashPassword(password)
  // eg. $2a$10$.ChY88hfPUYr8uQUCEVvNOIGNtZLXbZCzz9mpA6XRRs2hYAwh7pCS
  
  "HashingService" should {

    "correctly match valid password and hash" in {
      HashingService.isMatch(password, passwordHash) should equalTo(true)
    }

    "fail to match invalid password and hash" in {
      HashingService.isMatch("topSecret", passwordHash) should equalTo(false)
    }
  }
}