package de.hpi.ingestion.dataimport.wikidata

import de.hpi.ingestion.implicits.RegexImplicits._

/**
  * Strategies for the normalization of WikiData entities
  */
object WikiDataNormalizationStrategy extends Serializable {
	/**
	  * Normalizes coordinates: e.g. "-1,1"
	  * @param values coordinates list
	  * @return normalized coordinates list
	  */
	def normalizeCoords(values: List[String]): List[String] = {
		values.flatMap {
			case r"""([-+]?[0-9]+\.?[0-9]*)${lat};([-+]?[0-9]+\.?[0-9]*)${long}""" => List(lat, long)
			case _ => None
		}
	}

	/**
	  * Normalizes countries: e.g. "Q30", "Vereinigte Staaten"
	  * @param values country list
	  * @return normalized countries
	  */
	def normalizeCountry(values: List[String]): List[String] = {
		values.flatMap {
			case r"Q[0-9]+" => None
			case other => List(other)
		}
	}

	/**
	  * Normalizes sector
	  * @param values sector list
	  * @return normalized sectors
	  */
	def normalizeSector(values: List[String]): List[String] = {
		values.flatMap {
			case r"Q[0-9]+" => None
			case other => List(other)
		}
	}

	/**
	  * Normalizes employees
	  * @param values list of employees
	  * @return normalized employees
	  */
	def normalizeEmployees(values: List[String]): List[String] = {
		values.flatMap {
			case r"""\+(\d+)${employees};1""" => List(employees)
			case _ => None
		}
	}

	/**
	  * Chooses the right normalization method
	  * @param attribute Attribute to be normalized
	  * @return Normalization method
	  */
	def apply(attribute: String): (List[String]) => List[String] = {
		attribute match {
			case "gen_sectors" => this.normalizeSector
			case "geo_coords" => this.normalizeCoords
			case "geo_country" => this.normalizeCountry
			case "gen_employees" => this.normalizeEmployees
			case _ => identity
		}
	}
}