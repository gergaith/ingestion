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

package de.hpi.ingestion.dataimport.dbpedia

import org.scalatest.{FlatSpec, Matchers}
import com.holdenkarau.spark.testing.{RDDComparisons, SharedSparkContext}
import de.hpi.ingestion.dataimport.dbpedia.models.DBpediaEntity

class DBpediaImportRDDTest extends FlatSpec with Matchers with SharedSparkContext with RDDComparisons {
    "Triples" should "be tokenized into three elements" in {
        TestData.turtleRDD(sc)
            .map(DBpediaImport.tokenize)
            .collect
            .foreach { tripleList =>
                tripleList should have length 3
            }
    }

    they should "have namespace prefixes after cleaning" in {
        val prefixesList = TestData.prefixesList
        val parsed = DBpediaImport.dbpediaToCleanedTriples(TestData.turtleRDD(sc), prefixesList)
            .map { case List(a, b, c) => (a, b, c) }

        val expected = TestData.tripleRDD(sc).map(el => (el._1, el._2._1, el._2._2))
        assertRDDEquals(expected, parsed)
    }

    "DBpediaEntities" should "not be empty" in {
        val organisations = TestData.organisations
        val entities = TestData.tripleRDD(sc)
            .groupByKey
            .map(tuple => DBpediaImport.extractProperties(tuple._1, tuple._2.toList, organisations))
            .map(identity)
        entities should not be empty
    }

    they should "contain the same information as the triples" in {
        val organisations = TestData.organisations
        val entities = TestData.tripleRDD(sc)
            .groupByKey
            .map(tuple => DBpediaImport.extractProperties(tuple._1, tuple._2.toList, organisations))
            .map(identity)
        val expected = TestData.entityRDD(sc)
        assertRDDEquals(expected, entities)
    }
}
