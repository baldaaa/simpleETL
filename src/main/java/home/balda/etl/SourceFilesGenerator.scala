package home.balda.etl

import java.io.{File, PrintWriter}
import java.security.MessageDigest
import java.util.{Timer, TimerTask}

import org.apache.spark.streaming.scheduler.{StreamingListener, StreamingListenerReceiverStopped}
import sun.misc.BASE64Encoder

/**
  * Created by katia on 18/07/2017.
  */
object SourceFilesGenerator extends StreamingListener {


  val generatorURL = System.getenv(ETLConfig.ETL_SOURCE_FOLDER).split(":").last

  val timer = new Timer()

  def start(period: Long): Unit = {

    def timerTask() = generateFilesSet(generatorURL)
    timer.schedule(function2TimerTask(timerTask), System.getenv(ETLConfig.ETL_GENERATOR_DELAY).toInt * 1000, period * 1000)
  }

  implicit def function2TimerTask(f: () => Unit): TimerTask = {
    return new TimerTask {
      def run() = f()
    }
  }

  def generateFilesSet(path: String): Unit = {
    generateDescriptor(path)
  }

  def generateDescriptor(path: String): Unit = {

    val descFileName = StringBuilder.newBuilder
      .append(path)
      .append(System.getenv(ETLConfig.ETL_STATUS_PREFIX))
      .append("_")
      .append((System.currentTimeMillis()))
      .append(".")
      .append(System.getenv(ETLConfig.ETL_STATUS_SUFIX)).toString()

    try {
      val writer = new PrintWriter(new File(descFileName))

      try {

        for (i <- 1 to 10) {
          val timeStemp = System.currentTimeMillis()
          val dataFileName = StringBuilder.newBuilder
            .append(path)
            .append(System.getenv(ETLConfig.ETL_DATA_PREFIX))
            .append(i)
            .append(timeStemp)
            .append(".")
            .append(System.getenv(ETLConfig.ETL_DATA_SUFIX)).toString()

          val checksum = generateCSVFile(dataFileName)
          if (!checksum.isEmpty)
            writer.append(dataFileName + " " + checksum + "\n")
        }
      } finally
        writer.close()
    } catch {
      case e: Exception => {
        println("Connection to " + System.getenv(ETLConfig.ETL_SOURCE_FOLDER) + " lost")
      }
    }
  }

  def generateCSVFile(uri: String): String = {

    try {
      val writer = new PrintWriter(new File(uri))
      try {
        val builder = StringBuilder.newBuilder.append("AAA,BBB,CCC\n")
        for (i <- 1 to 10) {
          builder.append(scala.util.Random.nextInt(100))

          for (i <- 1 to 2) {
            val num = scala.util.Random.nextInt(100)
            builder.append(",").append(num)
          }
          builder.append("\n").toString()

        }
        writer.append(builder.toString())

        val md = MessageDigest.getInstance("MD5")
        val bytes = builder.toString().getBytes("UTF-8")
        return new BASE64Encoder().encode(md.digest(bytes))

      } finally writer.close()
    }

    catch {
      case e: Exception => {
        println("Connection to " + System.getenv(ETLConfig.ETL_SOURCE_FOLDER) + " lost")
        return "";
      }
    }
  }

  override def onReceiverStopped(receiverStopped: StreamingListenerReceiverStopped): Unit = {

    timer.cancel()
  }
}
