package models

import org.joda.time.DateTime
import reactivemongo.bson._
import reactivemongo.bson.BSONObjectID
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.modules.reactivemongo.json.BSONFormats._
import java.util.Date

case class Trip( destination: String,
                 startDate: Date,
                 endDate: Date,
                 comment: Option[String],
                 userId: Option[BSONObjectID],
                 _id: Option[BSONObjectID])
                 

object TripJsonFormats {
  // Generates Writes and Reads for Feed and User thanks to Json Macros
  implicit val tripFormat = Json.format[Trip]
}