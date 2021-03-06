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

package de.hpi.ingestion.textmining.re

import com.holdenkarau.spark.testing.{RDDComparisons, SharedSparkContext}
import de.hpi.ingestion.textmining.TestData
import de.hpi.ingestion.textmining.models.Cooccurrence
import org.scalatest.{FlatSpec, Matchers}

class CooccurrenceCounterTest extends FlatSpec with SharedSparkContext with Matchers with RDDComparisons {
    "All entity lists" should "be extracted" in {
        val sentences = TestData.sentenceList()
        val entityList = sentences.map(CooccurrenceCounter.sentenceToEntityList)
        entityList shouldEqual TestData.entityLists()
    }


    "Found cooccurrences" should "exactly these cooccurrences" in {
        val job = new CooccurrenceCounter
        job.sentences = sc.parallelize(TestData.sentencesWithCooccurrences())
        job.run(sc)
        val expectedCooccurrences = sc.parallelize(TestData.cooccurrences())
        assertRDDEquals(job.cooccurrences, expectedCooccurrences)
    }
}
