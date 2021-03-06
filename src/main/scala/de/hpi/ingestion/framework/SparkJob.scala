/*
Copyright 2016-17, Hasso-Plattner-Institut fuer Softwaresystemtechnik GmbH

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package de.hpi.ingestion.framework

import java.io.{BufferedReader, InputStream, InputStreamReader}

import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * Spark Job framework trait implementing the main method and defining the load, run and save methods.
  */
trait SparkJob extends Configurable with Serializable {
    var appName = "Ingestion Spark Job"
    val sparkOptions = mutable.Map[String, String]()
    val cassandraLoadQueries = ListBuffer[String]()
    val cassandraSaveQueries = ListBuffer[String]()
    var conf = CommandLineConf(Seq())

    /**
      * Loads a number of input RDDs and saves them in instance variables.
      * @param sc SparkContext to be used for the job
      */
    def load(sc: SparkContext): Unit

    /**
      * Executes the data processing of this job and produces the output data.
      * @param sc SparkContext to be used for the job
      */
    def run(sc: SparkContext): Unit

    /**
      * Saves the output data saved in instance variables to, e.g., the Cassandra or the HDFS.
      * @param sc SparkContext to be used for the job
      */
    def save(sc: SparkContext): Unit

    /**
      * Called before running the job. Used to assert specifics of the input arguments. Returns false if the program
      * should be terminated. Parses the xml config if a path is given in args or configFile is set.
      * @return true if the program can continue, false if it should be terminated
      */
    def assertConditions(): Boolean = {
        conf.configOpt.foreach(configFile = _)
        if(configFile.nonEmpty) parseConfig()
        conf.importConfigOpt.foreach(importConfigFile = _)
        if(importConfigFile.nonEmpty) parseImportConfig()
        true
    }

    /**
      * Creates the Spark Conf and sets extra options. appName is used as name and sparkOptions is used as
      * extra Spark options.
      * @return Spark Conf with the extra values set.
      */
    def createSparkConf(): SparkConf = {
        new SparkConf()
            .setAppName(appName)
            .setAll(sparkOptions.toList)
    }

    // $COVERAGE-OFF$
    /**
      * Executes multiple CQL queries on the Cassandra.
      * @param sc SparkContext to be used for the job
      * @param queries List of queries to execute
      */
    def executeQueries(sc: SparkContext, queries: List[String]): Unit = {
        CassandraConnector(sc.getConf).withSessionDo(session => queries.foreach(session.execute))
    }

    /**
      * Opens a HDFS file stream pointing a file in the HDFS.
      * @param file name of the file to be opened
      * @return Input Stream pointing to the file in the HDFS
      */
    def hdfsFileStream(file: String): InputStream = {
        val hadoopConf = new Configuration()
        val fs = FileSystem.get(hadoopConf)
        fs.open(new Path(file))
    }
    // $COVERAGE-ON$

    /**
      * Reads a file, parses each line and transforms these into a collection. By default the files are read from the
      * HDFS. This method can be called inside a map function to load the file into the memory of every worker.
      * @param file name of the file to read
      * @param fileStreamFunction function that opens an input stream on the specified file. The default function reads
      *                           the file from the HDFS.
      * @param parseLineFunction function that parses a single line. The option result type allows for removal of
      *                          unwanted lines
      * @param transformCollectionFunction function that transforms the list of parsed lines into any other type.
      * @tparam T type of a parsed line
      * @tparam S type of the result collection
      * @return specified collection of every parsed line
      */
    def parseHDFSFileToCollection[T, S](
        file: String,
        fileStreamFunction: String => InputStream = hdfsFileStream,
        parseLineFunction: String => Option[T],
        transformCollectionFunction: List[T] => S
    ): S = {
        val fileStream = fileStreamFunction(file)
        val reader = new BufferedReader(new InputStreamReader(fileStream, "UTF-8"))
        val parsedLineBuffer = ListBuffer.empty[T]
        var line = reader.readLine()
        while(line != null) {
            val parsedLine = parseLineFunction(line)
            parsedLine.foreach(parsedLineBuffer += _)
            line = reader.readLine()
        }
        transformCollectionFunction(parsedLineBuffer.toList)
    }

    /**
      * Reads a file, parses each line and adds these to a collection. By default the files are read from the HDFS.
      * This method can be called inside a map function to load the file into the memory of every worker.
      * @param file name of the file to read
      * @param fileStreamFunction function that opens an input stream on the specified file. The default function reads
      *                           the file from the HDFS.
      * @param createCollectionFunction function that creates an instance of a mutable collection that will contain
      *                                 every parsed line
      * @param parseLineFunction function that parses a line and adds it to the mutable collection
      * @tparam T type of the mutable collection that will contain every parsed line
      * @return specified mutable collection containing every parsed line
      */
    def parseHDFSFileToMutableCollection[T](
        file: String,
        fileStreamFunction: String => InputStream = hdfsFileStream,
        createCollectionFunction: () => T,
        parseLineFunction: (String, T) => Unit
    ): T = {
        val fileStream = fileStreamFunction(file)
        val reader = new BufferedReader(new InputStreamReader(fileStream, "UTF-8"))
        val parsedLineCollection = createCollectionFunction()
        var line = reader.readLine()
        while(line != null) {
            parseLineFunction(line, parsedLineCollection)
            line = reader.readLine()
        }
        parsedLineCollection
    }

    /**
      * Executes a Spark job which first asserts definable conditions and then loads, processes and saves the data.
      * @param sc SparkContext to be used for the job
      * @param args command line arguments to be used for the job
      */
    def execute(sc: SparkContext, args: Array[String] = Array()): Unit = {
        conf = CommandLineConf(args)
        if(!assertConditions()) {
            return
        }
        executeQueries(sc, cassandraLoadQueries.toList)
        load(sc)
        run(sc)
        executeQueries(sc, cassandraSaveQueries.toList)
        save(sc)
    }
}
