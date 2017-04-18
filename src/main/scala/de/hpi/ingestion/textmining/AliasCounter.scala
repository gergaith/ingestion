package de.hpi.ingestion.textmining

import org.apache.spark.{SparkConf, SparkContext}
import com.datastax.spark.connector._
import org.apache.spark.rdd.RDD
import de.hpi.ingestion.textmining.models._

object AliasCounter {
	val keyspace = "wikidumps"
	val inputArticlesTablename = "parsedwikipedia"
	val outputTablename = "wikipediaaliases"

	/**
	  * Calculates the probability that an alias is a link.
	  * @param aliasCounter AliasCounts of a given alias
	  * @return percentage of the occurences as link.
	  */
	def probabilityIsLink(aliasCounter: AliasCounts): Double = {
		aliasCounter.linkoccurrences.toDouble / aliasCounter.totaloccurrences
	}

	/**
	  * Extracts list of link and general alias occurences for an article
	  * @param entry article from which the aliases will be extracted
	  * @return list of aliases each with an occurence set to 1
	  */
	def extractAliasList(entry: ParsedWikipediaEntry): List[AliasCounts] = {
		val linkSet = entry.allLinks
			.map(_.alias)
			.toSet

		val links = linkSet
		    .toList
			.map(AliasCounts(_, 1, 1))

		val aliases = entry.foundaliases
			.toSet
			.filterNot(linkSet)
			.toList
			.map(AliasCounts(_, 0, 1))

		links ++ aliases
	}

	/**
	  * Reduces two AliasCounters of the same alias.
	  * @param alias1 first AliasCounts with the same alias as alias2
	  * @param alias2 second AliasCounts with the same alias as alias1
	  * @return AliasCounts with summed link and total occurences
	  */
	def aliasReduction(alias1: AliasCounts, alias2: AliasCounts): AliasCounts = {
		AliasCounts(
			alias1.alias,
			alias1.linkoccurrences + alias2.linkoccurrences,
			alias1.totaloccurrences + alias2.totaloccurrences)
	}

	/**
	  * Counts link and total occurences of link aliases in Wikipedia articles.
	  * @param articles RDD containing parsed wikipedia articles
	  * @return RDD containing alias counts for links and total occurences
	  */
	def countAliases(articles: RDD[ParsedWikipediaEntry]): RDD[AliasCounts] = {
		articles.flatMap(extractAliasList)
			.map(alias => (alias.alias, alias))
			.reduceByKey(aliasReduction(_, _))
			.map(_._2)
			.filter(_.alias.nonEmpty)
	}

	def main(args: Array[String]): Unit = {
		val conf = new SparkConf()
			.setAppName("Alias Counter")
			.set("spark.cassandra.connection.host", "odin01")

		val sc = new SparkContext(conf)

		val articles = sc.cassandraTable[ParsedWikipediaEntry](keyspace, inputArticlesTablename)
		countAliases(articles).cache
			.filter(_.alias.length <= 1000)
			.saveToCassandra(keyspace, outputTablename)

		sc.stop()
	}
}