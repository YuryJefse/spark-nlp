package com.johnsnowlabs.nlp.annotators.sda.vivekn

import com.johnsnowlabs.nlp.annotators.common.{IntStringMapParam, Tokenized, TokenizedSentence}
import com.johnsnowlabs.nlp.{Annotation, AnnotatorModel}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.ml.param.{IntParam, StringArrayParam}
import org.apache.spark.ml.util.{DefaultParamsReadable, Identifiable}

class ViveknSentimentModel(override val uid: String) extends AnnotatorModel[ViveknSentimentModel] {

  import com.johnsnowlabs.nlp.AnnotatorType._

  private val config: Config = ConfigFactory.load
  private val importantFeatureRatio = config.getDouble("nlp.viveknSentiment.importantFeaturesRatio")
  private val unimportantFeatureStep = config.getDouble("nlp.viveknSentiment.unimportantFeaturesStepRatio")
  private val featureLimit = config.getInt("nlp.viveknSentiment.featuresLimit")

  override val annotatorType: AnnotatorType = SENTIMENT

  override val requiredAnnotatorTypes: Array[AnnotatorType] = Array(TOKEN, DOCUMENT)

  protected val positive: IntStringMapParam = new IntStringMapParam(this, "positive_sentences", "positive sentences trained")
  protected val negative: IntStringMapParam = new IntStringMapParam(this, "negative_sentences", "negative sentences trained")
  protected val features: StringArrayParam = new StringArrayParam(this, "words", "unique words trained")
  protected val positiveTotals: IntParam = new IntParam(this, "positive_totals", "count of positive words")
  protected val negativeTotals: IntParam = new IntParam(this, "negative_totals", "count of negative words")

  def this() = this(Identifiable.randomUID("VIVEKN"))

  private[vivekn] def setPositive(value: Map[String, Int]) = set(positive, value)
  private[vivekn] def setNegative(value: Map[String, Int]) = set(negative, value)
  private[vivekn] def setPositiveTotals(value: Int) = set(positiveTotals, value)
  private[vivekn] def setNegativeTotals(value: Int) = set(negativeTotals, value)
  private[vivekn] def setWords(value: Array[String]) = {
    require(value.nonEmpty, "Word analysis for features cannot be empty. Set prune to false if training is small")
    val currentFeatures = scala.collection.mutable.Set.empty[String]
    val start = (value.length * importantFeatureRatio).ceil.toInt
    val afterStart = {
      if (featureLimit == -1) value.length
      else featureLimit
    }
    val step = (afterStart * unimportantFeatureStep).ceil.toInt
    value.take(start).foreach(currentFeatures.add)
    Range(start, afterStart, step).foreach(k => {
      value.slice(k, k+step).foreach(currentFeatures.add)
    })
    set(features, currentFeatures.toArray)
  }

  def classify(sentence: TokenizedSentence): Boolean = {
    val words = ViveknSentimentApproach.negateSequence(sentence.tokens.toList).intersect($(features)).distinct
    if (words.isEmpty) return true
    val positiveProbability = words.map(word => scala.math.log(($(positive).getOrElse(word, 0) + 1.0) / (2.0 * $(positiveTotals)))).sum
    val negativeProbability = words.map(word => scala.math.log(($(negative).getOrElse(word, 0) + 1.0) / (2.0 * $(negativeTotals)))).sum
    positiveProbability > negativeProbability
  }

  /**
    * Tokens are needed to identify each word in a sentence boundary
    * POS tags are optionally submitted to the model in case they are needed
    * Lemmas are another optional annotator for some models
    * Bounds of sentiment are hardcoded to 0 as they render useless
    * @param annotations Annotations that correspond to inputAnnotationCols generated by previous annotators if any
    * @return any number of annotations processed for every input annotation. Not necessary one to one relationship
    */
  override def annotate(annotations: Seq[Annotation]): Seq[Annotation] = {
    val sentences = Tokenized.unpack(annotations)

    sentences.map(sentence => {
      Annotation(
        annotatorType,
        sentence.indexedTokens.map(t => t.begin).min,
        sentence.indexedTokens.map(t => t.end).max,
        if (classify(sentence)) "positive" else "negative",
        Map.empty[String, String]
      )
    })
  }
}

object ViveknSentimentModel extends DefaultParamsReadable[ViveknSentimentModel]