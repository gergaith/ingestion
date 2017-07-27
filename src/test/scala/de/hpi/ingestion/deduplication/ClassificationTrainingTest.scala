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

package de.hpi.ingestion.deduplication

import com.holdenkarau.spark.testing.SharedSparkContext
import org.scalatest.{FlatSpec, Matchers}
import de.hpi.ingestion.implicits.CollectionImplicits._
import org.apache.spark.mllib.classification.NaiveBayesModel

class ClassificationTrainingTest extends FlatSpec with Matchers with SharedSparkContext {

	"Naive Bayes model" should "be returned" in {
		val entries = (0 to 10).flatMap(t => TestData.featureEntries)
		val input = List(sc.parallelize(entries)).toAnyRDD()
		val models = ClassificationTraining.run(input, sc).head.collect
		models should not be empty
		models.foreach { model =>
			model.isInstanceOf[NaiveBayesModel] shouldBe true }
	}
}
