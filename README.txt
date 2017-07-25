Project assignment:

In this assignment you are asked to build a working ETL process using Scala programming language.

The Data: 
You should generate the data that your system will process. 
Control File:
will hold the information of the files needed to be picked-up (path, size, timestamp, checksum) 
Data Files:
will be in CSV format and include headers. ( at least 3 data files) 

Assume you get a daily feed update.

 Location of feed:
A remote Unix directory accessible via ssh and scp protocols. 

You should use Docker instance with preset image of CentOS with SSH to do this.

The system: 
It should listen to the remote directory and pick feeds when ready. 
It should write the data files to a remote HDFS ( use a Docker instance with preset HDFS image) 

When it finishes writing a complete feed it should write a “message complete”
to a remote Kafka topic (spin-up a Docker with preset image of Kafka). 
“message complete”  should contain the data in the control file,
and the location on the HDFS where the data was written to.  

The system should be able to recover from failures (e.g.  un-planned reboot) 

You can assume the HDFS and KAFKA are always available.
Recommended technologies to use: Apache Spark and Apache Camel.


Implementation report:

Limitation:
1. The system able to write to host directory which is mount in system only(connector to remote directory via ssh and scp not implemented).

To clean,compile,package or/and execute app use maven goals:
clean compile package exec

Change below application arguments in pom.xml to relevant

        <kafka.topic>test</kafka.topic>
        <kafka.broker>10.100.102.2:9092</kafka.broker>
        <batch.duration.sec>30</batch.duration.sec>
        <destination.folder>hdfs://localhost:9000/testin/</destination.folder>
        <source.folder>file:///Users/katia/testin/</source.folder>
        <status.file.prefix>stat</status.file.prefix>
        <status.file.sufix>txt</status.file.sufix>
        <status.file.delimiter> </status.file.delimiter>
        <data.file.prefix>data</data.file.prefix>
        <data.file.sufix>csv</data.file.sufix>
        <sorce.generator.period.sec>10</sorce.generator.period.sec>
        <sorce.generator.delay.sec>10</sorce.generator.delay.sec>
