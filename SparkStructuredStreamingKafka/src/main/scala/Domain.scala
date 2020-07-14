import java.io.Serializable

object Domain {


  case class Event[A] (
                        event_name: Option[String],
                        event_id: Option[String],
                        time: Option[A],
                        event_url: Option[String]
                      )

  case class Facebook (
                        identifier: Option[String]
                      )

  case class Group  (
                      group_topics: Option[Seq[GroupTopics]],
                      group_city: Option[String],
                      group_country: Option[String],
                      group_id: Option[Long],
                      group_name: Option[String],
                      group_lon: Option[Double],
                      group_urlname: Option[String],
                      group_lat: Option[Double],
                      group_state: Option[String]
                    )

  case class GroupTopics  (
                            urlkey: Option[String],
                            topic_name: Option[String]
                          )

  case class OtherServices (
                             facebook: Option[Facebook],
                             twitter: Option[Facebook],
                             tumblr: Option[Facebook],
                             flickr: Option[Facebook],
                             linkedin: Option[Facebook]
                           )


  case class Member (
                      member_id: Option[Long],
                      photo: Option[String],
                      member_name: Option[String],
                      other_services: Option[OtherServices]
                    )


  case class Venue (
                     venue_name: Option[String],
                     lon: Option[Double],
                     lat: Option[Double],
                     venue_id: Option[Long]
                   )

  case class RSVPEvent[A] (
                            venue: Option[Venue],
                            visibility: Option[String],
                            response: Option[String],
                            guests: Option[Long],
                            member: Option[Member],
                            rsvp_id: Option[Long],
                            mtime: Option[A],
                            event: Option[Event[A]],
                            group: Option[Group]
                          )


  case class RSVPEventShort (

  mtime:Long,
  event_id:String,
  group_city:String,
  group_country:String,
  group_lat:Option[Double],
  group_lon:Option[Double],
  lat:Option[Double],
  lon:Option[Double],
  topic_name:Array[String]

                            )


  @SerialVersionUID(123L)
  case class LatestStreamOffset(offset:Long) extends Serializable



}
