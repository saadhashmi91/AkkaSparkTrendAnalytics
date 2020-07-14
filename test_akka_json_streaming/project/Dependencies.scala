import sbt._

object Dependencies {
  val scalaVer = "2.13.2"
  // #dependencies
  val ScalaTestVersion = "3.1.1"
  val AkkaVersion = "2.6.4"
  val AkkaHttpVersion = "10.1.11"
  val AlpakkaVersion = "2.0.0"
  val AlpakkaKafkaVersion = "2.0.2"
  val circeVersion = "0.12.3"
  val sparkVersion = "3.0.0"

  val dependencies = List(
    "com.lightbend.akka" %% "akka-stream-alpakka-json-streaming" % AlpakkaVersion,
    "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    // Used from Scala
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,

    //Kafka Test Containers
    "org.testcontainers" % "kafka" % "1.14.1",

    //circe for parsing json
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,

    // avro4s for encoding Kafka Records in Avro Format
    "com.sksamuel.avro4s" %% "avro4s-core" % "3.1.1",
    "com.sksamuel.avro4s" %% "avro4s-kafka" % "3.1.1",

    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.3"
  )
  // #dependencies
}