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

package de.hpi.ingestion.textmining.preprocessing

import com.datastax.spark.connector._
import de.hpi.ingestion.framework.SparkJob
import de.hpi.ingestion.implicits.CollectionImplicits._
import de.hpi.ingestion.textmining.models.{DocumentFrequency, ParsedWikipediaEntry, WikipediaArticleCount}
import de.hpi.ingestion.textmining.tokenizer.IngestionTokenizer
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

/**
  * Ccunts `DocumentFrequencies` over all articles.
  */
object DocumentFrequencyCounter extends SparkJob {
	appName = "Document Frequency Counter"
	configFile = "textmining.xml"
	val removeStopwords = true
	val stem = true
	/**
	  * This value must be smaller than or equal to the number of considered documents.
	  */
	var leastSignificantDocumentFrequency = 5

	// $COVERAGE-OFF$
	/**
	  * Loads Parsed Wikipedia entries from the Cassandra.
	  * @param sc Spark Context used to load the RDDs
	  * @param args arguments of the program
	  * @return List of RDDs containing the data processed in the job.
	  */
	override def load(sc: SparkContext, args: Array[String]): List[RDD[Any]] = {
		val articles = sc.cassandraTable[ParsedWikipediaEntry](settings("keyspace"), settings("parsedWikiTable"))
		List(articles).toAnyRDD()
	}

	/**
	  * Saves Document Frequencies to the Cassandra.
	  * @param output List of RDDs containing the output of the job
	  * @param sc Spark Context used to connect to the Cassandra or the HDFS
	  * @param args arguments of the program
	  */
	override def save(output: List[RDD[Any]], sc: SparkContext, args: Array[String]): Unit = {
		val List(df, articleCount) = output
		df
			.asInstanceOf[RDD[DocumentFrequency]]
			.saveToCassandra(settings("keyspace"), settings("dfTable"))
		articleCount
			.asInstanceOf[RDD[WikipediaArticleCount]]
			.saveToCassandra(settings("keyspace"), settings("articleCountTable"))
	}
	// $COVERAGE-ON$

	/**
	  * Counts document frequencies while stemming and removing stopwords.
	  *
	  * @param articles all parsed Wikipedia entries
	  * @return RDD of document frequencies
	  */
	def countDocumentFrequencies(
		articles: RDD[ParsedWikipediaEntry]
	): RDD[DocumentFrequency] = {
		val tokenizer = IngestionTokenizer(removeStopwords, stem)
		articles
			.flatMap(article => tokenizer.process(article.getText()).toSet)
			.map(word => (word, 1))
			.reduceByKey(_ + _)
			.map(DocumentFrequency.tupled)
	}

	/**
	  * Filters all document frequencies that do not meet the threshold.
	  *
	  * @param documentFrequencies document frequencies to be filtered
	  * @param threshold           minimum amount of occurrences in documents
	  * @return filtered document frequencies
	  */
	def filterDocumentFrequencies(
		documentFrequencies: RDD[DocumentFrequency],
		threshold: Int
	): RDD[DocumentFrequency] = {
		documentFrequencies
			.filter(documentFrequency => documentFrequency.count >= threshold)
	}

	/**
	  * Counts `DocumentFrequencies` over all articles.
	  *
	  * @param input List of RDDs containing the input data
	  * @param sc Spark Context used to e.g. broadcast variables
	  * @param args arguments of the program
	  * @return List of RDDs containing the output data
	  */
	override def run(input: List[RDD[Any]], sc: SparkContext, args: Array[String] = Array()): List[RDD[Any]] = {
		val articles = input.fromAnyRDD[ParsedWikipediaEntry]().head
		val numArticles = articles.asInstanceOf[RDD[ParsedWikipediaEntry]].count
		val articleCount = WikipediaArticleCount("parsedwikipedia", BigInt(numArticles))
		val articleCountRDD = sc.parallelize(List(articleCount))
		val documentFrequencies = countDocumentFrequencies(articles)
		val filteredDFs = filterDocumentFrequencies(documentFrequencies, leastSignificantDocumentFrequency)
		List(filteredDFs).toAnyRDD() ++ List(articleCountRDD).toAnyRDD()
	}
}