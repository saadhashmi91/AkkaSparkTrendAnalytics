import java.util.UUID

import Domain.RSVPEvent
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.kafka.scaladsl.Producer
import akka.kafka.{ConsumerSettings, ProducerSettings}
import akka.stream.scaladsl.{JsonFraming, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.sksamuel.avro4s.BinaryFormat
import com.sksamuel.avro4s.kafka.GenericSerde
import io.circe.generic.auto._
import io.circe.parser._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.testcontainers.containers.KafkaContainer

import scala.concurrent.Future

object Main extends App  {

  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "alpakka-samples")

  import actorSystem.executionContext

  val httpRequest = HttpRequest(uri = "http://stream.meetup.com/2/rsvps")



  def extractEntityData(response: HttpResponse): Source[ByteString, _] =
    response match {
      case HttpResponse(_, _, entity, _) => entity.dataBytes
      case notOkResponse =>
        Source.failed(new RuntimeException(s"illegal response $notOkResponse"))
    }



  val kafkaBroker: KafkaContainer = new KafkaContainer().withExposedPorts(9093)
  kafkaBroker.start()

  private val bootstrapServers: String = kafkaBroker.getBootstrapServers()


  val jsonFramingScanner=JsonFraming.objectScanner(Int.MaxValue)


  val kafkaProducerSettings = ProducerSettings(actorSystem.toClassic, new StringSerializer, new GenericSerde[RSVPEvent[Long]](BinaryFormat))
    .withBootstrapServers(bootstrapServers)




  def consoleSink: Sink[ProducerRecord[String, RSVPEvent[Long]], Future[Done]] = Sink.foreach[ProducerRecord[String, RSVPEvent[Long]]] {
  case message:ProducerRecord[String, RSVPEvent[Long]] => println(message.value())

}

  def sink: Sink[ProducerRecord[String, RSVPEvent[Long]], Future[Done]]= Producer.plainSink[String,RSVPEvent[Long]](kafkaProducerSettings)

  val source:Source[ProducerRecord[String, RSVPEvent[Long]],NotUsed] =
    Source
      .single(httpRequest) //: HttpRequest
      .mapAsync(1)(Http()(actorSystem.toClassic).singleRequest(_)) //: HttpResponse
      .flatMapConcat(extractEntityData) //: ByteString
      .via(jsonFramingScanner) //: ByteString
      .map(_.utf8String)      // In case you have ByteString
      .map(decode[RSVPEvent[Long]](_))  // Using Circe Unmarshaller here
      .map { elem =>
      val record = elem match {
        case Right(value) => Option(value)
        case Left(error) => None

      }
    record
    }.filter(_.nonEmpty).map{  filtered =>


      val out= new ProducerRecord("rsvp_events", UUID.randomUUID().toString,filtered.get) //: Kafka ProducerRecord
      out
    }

   val done: Future[Done] = source.runWith(sink)






  val cs: CoordinatedShutdown = CoordinatedShutdown(actorSystem)
  cs.addTask(CoordinatedShutdown.PhaseServiceStop, "shut-down-client-http-pool")( () =>
    Http()(actorSystem.toClassic).shutdownAllConnectionPools().map(_ => Done)
  )

  val kafkaConsumerSettings = ConsumerSettings(actorSystem.toClassic, new StringDeserializer,  new GenericSerde[RSVPEvent[Long]](BinaryFormat))
    .withBootstrapServers(bootstrapServers)
    .withGroupId("rsvp_events_consumer")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  //Kafka Consumer for testing purposes

  /*val control = Consumer
    .atMostOnceSource(kafkaConsumerSettings, Subscriptions.topics("rsvp_events"))
    .map(_.value)
    .toMat(Sink.foreach(println))(Keep.both)
    .mapMaterializedValue(Consumer.DrainingControl.apply)
    .run()
*/

  for {
    _ <- done

  } {
    kafkaBroker.stop()

    cs.run(CoordinatedShutdown.UnknownReason)
  }





}
