package de.hpi.ingestion.implicits

object TestData {

	def crossableLists: (List[String], List[String]) = {
		(List("a1", "a2"), List("b1", "b2", "b3"))
	}

	def crossProduct: Set[(String, String)] = {
		Set(
			("a1", "b1"),
			("a1", "b2"),
			("a1", "b3"),
			("a2", "b1"),
			("a2", "b2"),
			("a2", "b3")
		)
	}

	def diffLists: (List[Int], List[Int]) = {
		(List(1, 2, 3, 4), List(3, 4, 5, 6))
	}

	def equalDiffLists: (List[Int], List[Int]) = {
		(List(1, 2, 3, 4), List(1, 4, 3, 2))
	}

	def diffString: String = {
		"Difference:\nx - y:\n\t1\n\t2\ny - x:\n\t5\n\t6"
	}

	def equalDiffString: String = {
		"Difference:\nx - y:\n\t\ny - x:\n\t"
	}
}
