import java.io.ByteArrayInputStream
import java.sql.Timestamp

import Domain.{RSVPEvent, RSVPEventShort}
import com.github.dockerjava.api.model.ExposedPort
import com.sksamuel.avro4s.{AvroInputStream, AvroSchema}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{GroupState, Trigger}
import org.apache.spark.sql.{Dataset, Row, SparkSession}
import org.testcontainers.DockerClientFactory

import scala.collection.JavaConversions._
import scala.reflect.runtime.universe.TypeTag

object Main extends App {

//val  storagePath = Option(System.getenv("fileStoragePath"))

  val spark = SparkSession
    .builder
    .appName("EWMATest")
    .master("local[*]")
    .config("spark.sql.streaming.checkpointLocation", "./")
    .getOrCreate()

import spark.implicits._

  // Creating docker-client to interact with the host's docker deamon to inspect running kafka containers
  val dockerClient  = DockerClientFactory.lazyClient

  val containerImage = "confluentinc/cp-kafka:5.2.1"

  // Try to get the mapped port for kafka borker using the docker-java client API
  import com.github.dockerjava.api.model.Ports

  var binding = new Array[Ports.Binding](0)

  val out=  dockerClient.listContainersCmd().exec().toList.filter(c => c.getImage == containerImage)
    .filter(c => c.getNetworkSettings() != null && c.getNetworkSettings().getNetworks() != null)

   if (out!=null){

     val inspectResult = dockerClient.inspectContainerCmd(out.head.getId).exec()

     binding = inspectResult.getNetworkSettings.getPorts.getBindings.get(new ExposedPort(9093))
   }

 val mappedPort= if (binding != null && binding.length > 0 && binding(0) != null)  Integer.valueOf(binding(0).getHostPortSpec)

  // If successful print the kafka bootstrap servers address
  println("Bootstrap servers are: localhost:"+mappedPort)

  // A spark UDF to deserialze Avro Encoded RSVPEvent[Long] Records
  val deser = udf { (input: Array[Byte]) =>
    deserializeMessage(input)
  }

  // Gets the schema by prividing the case class type
  val schema = AvroSchema[RSVPEvent[Long]]

  // Deserialization Function: Takes ByteArray and  outputs a record of RSVPEvent[Long]
  private def deserializeMessage(bytes: Array[Byte]):RSVPEvent[Long] = {


    val in = new ByteArrayInputStream(bytes)
    val input =  AvroInputStream.binary[RSVPEvent[Long]].from(in).build(schema)
    val out = input.iterator.next
    (out)
  }

  // Utility Methods to get  RSVPEvent[T] with the intended type T (Either Long or java.sql.Timestamp)
  def convertToRSVPEventExternal[T <: Long: TypeTag](df:Dataset[Row]): Dataset[RSVPEvent[T]] =  df.as[RSVPEvent[T]]
  def convertToRSVPEventInternal[T <: Timestamp: TypeTag](df:Dataset[Row]): Dataset[RSVPEvent[T]] =  df.as[RSVPEvent[T]]


// Create a spark structured stream from kafka source and desearialze the records using the UDF mentioned above
  val df = spark
    .readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", "localhost:"+mappedPort)
    .option("subscribe", "rsvp_events")
    .load()
    .select(deser(col("value")).as("value"))
    .select("value.*")


  // Turn off Spark Logging
  import org.apache.log4j.{Level, Logger}

  Logger.getLogger("org").setLevel(Level.OFF)
  Logger.getLogger("akka").setLevel(Level.OFF)


  // Convert to RSVPEvent[Long] Dataset
  val jsonDSExternal = convertToRSVPEventExternal[Long](df)

  // Filtered stream to include only "yes" responses of RSVPS and projecting only relevant fields for topic aggregation downstream
  val filteredDS =jsonDSExternal.filter('response ==="yes").select('mtime,$"event.event_id",$"group.group_city",$"group.group_country",$"group.group_lat",$"group.group_lon",$"venue.lat",$"venue.lon",$"group.group_topics.topic_name").as[RSVPEventShort]


  /** EWMA : Exponential Weigted Moving Average Calculation ***
    *  See :  http://pandas.pydata.org/pandas-docs/stable/computation.html#exponentially-weighted-windows
    *  for explaination of EWMA
    */


  // Period Length is an empirical length (days,hours or seconds) which is used to calculate the number of window periods passed between two consecutive events
  // by dividing it by the difference of two events' timestamps:  number_periods_passed = t2-t1 / period_length
  val period_length = 10000  // every 10 seconds

  private val logDecayPerPeriod = math.log(1.0 - 0.5)

  def getDecay(periods: Double): Double = math.exp(periods * logDecayPerPeriod)

  type EventId = String
  type State = ExponentialWeightedMovingAverageState
  case class ExponentialWeightedMovingAverageState(
                                                    eventId: EventId,
                                                    var eventTopics: Array[String],
                                                    var primaryESValue: Double,
                                                   // var auxiliaryESValue: Double,
                                                    var time: Long,
                                                    var count: Long
                                                  )

  case class ExponentialWeightedMovingAverageOutput(ewma: Double)

  def zero(eventId: EventId,eventTopics:Array[String]=Array.empty): ExponentialWeightedMovingAverageState =
    ExponentialWeightedMovingAverageState(
      eventId,
      eventTopics,
      primaryESValue = 0.0,
      //auxiliaryESValue = 0.0,
      time = 0L,
      count = 0L
    )

  def EWMAPerEvent(
                            event_id: String,
                            events: Iterator[RSVPEventShort],
                            state: GroupState[State]): Iterator[ExponentialWeightedMovingAverageState] = {

    def timestampToPeriods(timestamp1:Long,timestamp2:Long,period:Long) = {
      { (timestamp2 - timestamp1 )/ period}.toDouble
    }

    val values = events.toSeq

    val topics = if (!events.isEmpty) events.toList.head.topic_name else Array[String]()
    val initialState: State = zero(event_id,topics)
    val oldState = state.getOption.getOrElse(initialState)

   val newState = if (values.length == 0L) {
      oldState
    }  else {
     val newU = zero(event_id,oldState.eventTopics)
     val periods = timestampToPeriods(oldState.time, values.map(_.mtime).max,period_length)

     val decay = getDecay(periods)
     newU.primaryESValue = decay * oldState.primaryESValue + values.length * 0.5
     //newU.auxiliaryESValue = decay * oldState.auxiliaryESValue + u2.auxiliaryESValue
     newU.eventTopics= values.head.topic_name
     newU.time = values.map(_.mtime).max
     newU.count = values.length + oldState.count
     newU
   }
    state.update(newState)

    Iterator(newState)
  }

  val eventId: RSVPEventShort => EventId = { case RSVPEventShort(mtime, event_id,_,_,_,_,_,_,_) => event_id }
  val eventsByEventId = filteredDS.groupByKey(eventId)

  import org.apache.spark.sql.streaming.{GroupStateTimeout, OutputMode}


  val eventsWithEWMA = eventsByEventId.flatMapGroupsWithState(
    outputMode = OutputMode.Append,
    timeoutConf = GroupStateTimeout.NoTimeout)(EWMAPerEvent)
  // df.printSchema()
  import scala.concurrent.duration._
  val query= eventsWithEWMA.writeStream
  .format("console").
    option("truncate", false).
    trigger(Trigger.ProcessingTime(10.seconds)).
    outputMode(OutputMode.Append).start

 query.awaitTermination
  query.stop()


}
