package com.johnsnowlabs.nlp.annotators.sbd.pragmatic

import com.johnsnowlabs.nlp.annotators.common.{Sentence, SentenceSplit}
import com.johnsnowlabs.nlp.{Annotation, AnnotatorModel}
import org.apache.spark.ml.param.{BooleanParam, StringArrayParam}
import org.apache.spark.ml.util.{DefaultParamsReadable, Identifiable}

/**
  * Annotator that detects sentence boundaries using any provided approach
  * @param uid internal constructor requirement for serialization of params
  * @@ model: Model to use for boundaries detection
  */
class SentenceDetector(override val uid: String) extends AnnotatorModel[SentenceDetector] {

  import com.johnsnowlabs.nlp.AnnotatorType._

  val useAbbrevations = new BooleanParam(this, "useAbbreviations", "whether to apply abbreviations at sentence detection")

  val customBounds: StringArrayParam = new StringArrayParam(
    this,
    "customBounds",
    "characters used to explicitly mark sentence bounds"
  )

  def this() = this(Identifiable.randomUID("SENTENCE"))

  def setCustomBoundChars(value: Array[String]): this.type = set(customBounds, value)

  def setUseAbbreviations(value: Boolean): this.type = set(useAbbrevations, value)

  override val annotatorType: AnnotatorType = DOCUMENT

  override val requiredAnnotatorTypes: Array[AnnotatorType] = Array(DOCUMENT)

  setDefault(inputCols, Array(DOCUMENT))

  setDefault(useAbbrevations, false)

  lazy val model = new PragmaticMethod($(useAbbrevations))

  def tag(document: String): Seq[Sentence] = {
    model.extractBounds(
      document,
      get(customBounds).getOrElse(Array.empty[String])
    )
  }

  /**
    * Uses the model interface to prepare the context and extract the boundaries
    * @param annotations Annotations that correspond to inputAnnotationCols generated by previous annotators if any
    * @return One to many annotation relationship depending on how many sentences there are in the document
    */
  override def annotate(annotations: Seq[Annotation]): Seq[Annotation] = {
    val docs = annotations.map(_.result)
    val sentences = docs.flatMap(doc => tag(doc))
    SentenceSplit.pack(sentences)
  }
}

object SentenceDetector extends DefaultParamsReadable[SentenceDetector]