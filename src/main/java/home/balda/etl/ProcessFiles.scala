package home.balda.etl

/**
  * Created by katia on 17/07/2017.
  */
import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat
import org.apache.spark.SparkConf
import org.apache.spark.rdd.{RDD, UnionRDD}
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.{Seconds, StreamingContext}

import scala.reflect.ClassTag
object ProcessFiles {

  val delim = if(System.getenv(ETLConfig.ETL_STATUS_DELIMITER).isEmpty) " " else System.getenv(ETLConfig.ETL_STATUS_DELIMITER)


  def process(sc: StreamingContext, conf: SparkConf): Unit = {

    val filter = new Function[Path, Boolean] {
      def apply(x: Path): Boolean = {
        val flag = if (x.toString.split("/").last.startsWith(System.getenv(ETLConfig.ETL_STATUS_PREFIX))) true else false
        return flag
      }
    }

    val descriptorFile = sc.fileStream[LongWritable, Text, TextInputFormat](System.getenv(ETLConfig.ETL_SOURCE_FOLDER), filter, false)
      .transform(rdd =>
        new UnionRDD(rdd.context,
          rdd.dependencies.map(dep =>
            dep.rdd.asInstanceOf[RDD[(LongWritable, Text)]].map(_._2.toString).setName(dep.rdd.name)
          )
        )
      )

    val transformed = descriptorFile.
      transform(rdd => extractFileName(rdd, byRDDFileTransformer))
    transformed.foreachRDD(rdd => {
      val parts = rdd.partitions
      for (p <- parts) {
        val idx = p.index
        val partRdd = rdd.mapPartitionsWithIndex {
          case(index:Int,value:Iterator[(String,String)]) =>
            if (index == idx) value else Iterator()}
        val eventData = partRdd.map(f=>(f._1,f._2, writeDataFile(f._2.split(delim)(0))))
          .collect()
          KafkaWriter.createAndSend(eventData)
      }
    })

    def writeDataFile(uri: String): String = {
      
      val name = StringBuilder.newBuilder
        .append(System.getenv(ETLConfig.ETL_DEST_FOLDER))
        .append(uri.split("/").last.split("\\.")(0))
        .append("_")
        .append(System.currentTimeMillis())
        .append(".")
        .append(uri.split("/").last.split("\\.").last).toString()

      val file = SparkSession.builder.config(conf).getOrCreate()
        .read.textFile(uri).rdd
      file.saveAsTextFile(name)

      return name
    }
  }

  def byRDDFileTransformer(filename: String)(rdd: RDD[String]): RDD[(String, String)] =
    rdd.map(line => (filename, line))


  def extractFileName[U: ClassTag](unionrdd: RDD[String],
                                   transformFunc: String => RDD[String] => RDD[U]): RDD[U] = {
    new UnionRDD(unionrdd.context,
      unionrdd.dependencies.flatMap { dep =>
        if (dep.rdd.isEmpty) None
        else {
          val filename = dep.rdd.name
          Some(
            transformFunc(filename)(dep.rdd.asInstanceOf[RDD[String]])
              .setName(filename)
          )
        }
      }
    )
  }



  def main(args: Array[String]) {

    SourceFilesGenerator.start(System.getenv(ETLConfig.ETL_GENERATOR_PERIOD).toInt)

    val conf = new SparkConf().setAppName("ETL").setMaster("local[4]")
    val sc = new StreamingContext(conf, Seconds(System.getenv(ETLConfig.ETL_BATCH_DURATION).toInt))
    sc.addStreamingListener(KafkaWriter)
    sc.addStreamingListener(SourceFilesGenerator)

    process(sc, conf)

    sc.start()
    sc.awaitTermination()

  }

}
