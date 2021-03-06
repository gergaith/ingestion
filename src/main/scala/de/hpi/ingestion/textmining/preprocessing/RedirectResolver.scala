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
import de.hpi.ingestion.implicits.TupleImplicits._
import de.hpi.ingestion.textmining.models._
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

/**
  * Resolves all redirects for each `ParsedWikipediaEntry` by replacing them with the pages they point to and writing
  * the redirects to a cassandra table.
  */
class RedirectResolver extends SparkJob {
    import RedirectResolver._
    appName = "Redirect Resolver"
    configFile = "textmining.xml"

    var parsedWikipedia: RDD[ParsedWikipediaEntry] = _
    var resolvedParsedWikipedia: RDD[ParsedWikipediaEntry] = _
    var wikipediaRedirects: RDD[Redirect] = _
    var savedWikipediaRedirects: RDD[Redirect] = _

    // $COVERAGE-OFF$
    /**
      * Loads Parsed Wikipedia entries from the Cassandra.
      * @param sc Spark Context used to load the RDDs
      */
    override def load(sc: SparkContext): Unit = {
        parsedWikipedia = sc.cassandraTable[ParsedWikipediaEntry](settings("keyspace"), settings("parsedWikiTable"))
        wikipediaRedirects = sc.cassandraTable[Redirect](settings("keyspace"), settings("redirectTable"))
    }

    /**
      * Saves Parsed Wikipedia entries with resolved redirects
      * and the redirects themselves to the Cassandra.
      * @param sc Spark Context used to connect to the Cassandra or the HDFS
      */
    override def save(sc: SparkContext): Unit = {
        resolvedParsedWikipedia.saveToCassandra(settings("keyspace"), settings("parsedWikiTable"))
        if(!savedWikipediaRedirects.isEmpty()) {
            savedWikipediaRedirects.saveToCassandra(settings("keyspace"), settings("redirectTable"))
        }
    }
    // $COVERAGE-ON$

    /**
      * Resolves redirects for every ParsedWikipediaEntry. It checks if redirects where already found,
      * if not it finds all redirects, resolves transitive redirects
      * and then replaces links to redirect pages with links to the page the redirect directs to.
      * @param sc Spark Context used to e.g. broadcast variables
      */
    override def run(sc: SparkContext): Unit = {
        var redirects = wikipediaRedirects
            .map(Redirect.unapply(_).get)
            .collect
            .toMap
        val saveRedirectsToCassandra = redirects.isEmpty
        if(saveRedirectsToCassandra) {
            val redirectMap = buildRedirectDict(parsedWikipedia)
            redirects = resolveTransitiveRedirects(redirectMap)
        }
        val dictBroadcast = sc.broadcast(redirects)
        resolvedParsedWikipedia = parsedWikipedia
            .mapPartitions({ entryPartition =>
                val localDict = dictBroadcast.value
                entryPartition.map(entry => resolveRedirects(entry, localDict))
            }, true)
        val redirectsList = if(saveRedirectsToCassandra) redirects.map(Redirect.tupled).toList else Nil
        savedWikipediaRedirects = sc.parallelize(redirectsList)
    }
}

object RedirectResolver {
    /**
      * Resolves all redirects in the links of an articles. It does so by replacing the target page with the page the
      * redirect points to.
      *
      * @param entry Parsed Wikipedia Entry containing the links that will be cleaned
      * @param dict  Map containing all redirects
      * @return Parsed Wikipedia Entry with no redirect pages as target of a link
      */
    def resolveRedirects(entry: ParsedWikipediaEntry, dict: Map[String, String]): ParsedWikipediaEntry = {
        entry.allLinks().foreach(link => link.page = dict.getOrElse(link.page, link.page))
        entry
    }

    /**
      * Builds Map of redirects from redirect articles.
      *
      * @param articles RDD of Parsed Wikipedia Articles
      * @return Map containing the redirects in the form redirect page -> target page
      */
    def buildRedirectDict(articles: RDD[ParsedWikipediaEntry]): Map[String, String] = {
        articles
            .filter(TextParser.isRedirectPage)
            .map(entry => (entry.title, entry.textlinks.headOption.map(_.page)))
            .filter(t => t._2.isDefined && t._1 != t._2.get)
            .map(_.map(identity, _.get))
            .collect()
            .toMap
    }

    /**
      * Resolves transitive redirects by replacing the target page with the transitive target page. Also removes
      * all reflexive entries.
      *
      * @param redirectMap Map containing the redirects that will be cleaned
      * @return Map containing the cleaned redirects
      */
    def resolveTransitiveRedirects(redirectMap: Map[String, String]): Map[String, String] = {
        var resolvedRedirects = redirectMap
        var resolvableEntries = Map[String, String]()
        var visited = Set[String]()
        do {
            resolvedRedirects ++= resolvableEntries.map(_.map(identity, resolvedRedirects))
            resolvedRedirects = resolvedRedirects.filter(t => t._1 != t._2)

            visited ++= resolvableEntries.values
            resolvableEntries = resolvedRedirects
                .filter(t => resolvedRedirects.contains(t._2))
                .filter(t => !visited.contains(t._2))
        } while(resolvableEntries.nonEmpty)
        resolvedRedirects
    }
}
