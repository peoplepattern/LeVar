package levar

import play.api.libs.json._
import org.joda.time.DateTime
import levar.util.eitherAsAny
import levar.json._

/**
 * Data type of items (or rows) in a [[Dataset]]
 *
 * @param data the data blob in the item -- the stuff to predict on
 * @param value the value -- the truth to predict, if available
 * @param id identifier for the item -- can be provided by the user or generated, unique WRT the dataset
 * @param createdAt the date the item was put in the DB
 * @param labels labels applied to the data item
 * @param comments comments made on the data item
 */
case class Datum(
    data: JsValue,
    value: Option[Either[Double, String]] = None,
    id: Option[String] = None,
    createdAt: Option[DateTime] = None,
    labels: Option[Seq[String]] = None,
    comments: Option[ResultSet[Comment]] = None) {

  def valueAsAny: Option[Any] = value.map(eitherAsAny)

  def dataByCol(cols: Seq[String]): Seq[Any] = {
    val dataMap = data.as[Map[String, Either[Double, String]]].mapValues(eitherAsAny)
    for (col <- cols) yield {
      if (col.equalsIgnoreCase("id")) {
        id.getOrElse("")
      } else if (col.equalsIgnoreCase(Dataset.RegressionType.colname)) {
        val Some(Left(scr)) = value
        scr
      } else if (col.equalsIgnoreCase(Dataset.ClassificationType.colname)) {
        val Some(Right(cls)) = value
        cls
      } else {
        dataMap(col)
      }
    }
  }
}
