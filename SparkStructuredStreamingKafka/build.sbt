
name := "SparkStructuredStreamingKafka"

version := "0.1"

scalaVersion := "2.12.6"

val sparkVersion = "3.0.0"

enablePlugins(JavaAppPackaging)
libraryDependencies ++= Seq(
  //Apache Spark
  // https://mvnrepository.com/artifact/org.apache.spark/spark-core
  "org.apache.spark" %% "spark-core" % sparkVersion,
  // https://mvnrepository.com/artifact/org.apache.spark/spark-sql
  "org.apache.spark" %% "spark-sql"          % sparkVersion,
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,

  //kafka test containers
  //Docker Java
  "org.testcontainers" % "kafka" % "1.14.1",

  // avro4s for encoding Kafka Records in Avro Format
  "com.sksamuel.avro4s" %% "avro4s-core" % "3.1.1",
  "com.sksamuel.avro4s" %% "avro4s-kafka" % "3.1.1"

)