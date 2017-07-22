package home.balda.etl

import java.util

import com.google.gson.{Gson, GsonBuilder}
import home.balda.etl.ETLConfig.ETL_KAFKA_BROKER
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.spark.streaming.scheduler.StreamingListener

/**
  * Created by katia on 18/07/2017.
  */
object KafkaWriter extends StreamingListener {


  val delim = if(System.getenv(ETLConfig.ETL_STATUS_DELIMITER).isEmpty) " " else System.getenv(ETLConfig.ETL_STATUS_DELIMITER)

  def kafkaConfig(): util.HashMap[String, Object] = {
    val props = new util.HashMap[String, Object]()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv(ETL_KAFKA_BROKER))
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
      "org.apache.kafka.common.serialization.StringSerializer")
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
      "org.apache.kafka.common.serialization.StringSerializer")

    return props
  }

  val producer = new KafkaProducer[String, String](kafkaConfig())


  def createAndSend(data: Array[(String,String,String)]):Unit = {

    val event = new KafkaEvent(data(0)._1, "Task complete")
    data.foreach(d =>{
      event.addData( new KafkaEventData(d._2.split(delim)(0), d._2.split(delim)(1), d._3))
    })

    val jsonString = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(event)

    producer.send(new ProducerRecord[String, String](System.getenv(ETLConfig.ETL_KAFKA_TOPIC), jsonString))
    producer.flush()
    println("Sent to Kafka: " + jsonString)

  }



  import org.apache.spark.streaming.scheduler.StreamingListenerReceiverStopped

  override def onReceiverStopped(receiverStopped: StreamingListenerReceiverStopped): Unit = {
    producer.close()
  }

  class KafkaEvent {
    val data = new util.ArrayList[KafkaEventData]()
    var statusFile = "unknown";
    var message = "unknown";
    def this(s: String, m: String){
      this()
      statusFile = s
      message = m
    }
    def addData(d: KafkaEventData): Unit = {
      data.add(d)
    }
  }

  case class KafkaEventData(sourcePath: String, checksum: String, destPath: String)

}

