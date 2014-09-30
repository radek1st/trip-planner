package models

import org.joda.time.DateTime
import reactivemongo.bson._
import reactivemongo.bson.BSONObjectID
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.modules.reactivemongo.json.BSONFormats._
import java.util.Date
                 
case class User( email: String,
                 password: String,
                 role: Option[String],
                 _id: Option[BSONObjectID])

object Role extends Enumeration {
    type Role = Value
    val admin, user = Value
    
    def isAdmin(role: String): Boolean = {
      admin.toString().equalsIgnoreCase(role)
    }
}

object UserJsonFormats {
  // Generates Writes and Reads for Feed and User thanks to Json Macros
  implicit val userFormat = Json.format[User]
}