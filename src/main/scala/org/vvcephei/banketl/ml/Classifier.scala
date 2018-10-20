package org.vvcephei.banketl.ml

import java.io.StringReader

import opennlp.model.{HashSumEventStream, TwoPassDataIndexer}
import opennlp.tools.doccat._
import opennlp.tools.util.PlainTextByLineStream
import org.vvcephei.banketl.BankEtlTransaction
import org.vvcephei.scalaledger.lib.model.Transaction


case class Guess(value: String, confidence: Double)

case class Classification(guesses: List[Guess]) {
  def top(n: Int): List[Guess] = guesses take n
}

case class Classifier(training: List[Transaction], ledgerAccounts: Set[String], debug: Boolean = false) {
  private val tokenizer = Serializer(ledgerAccounts)
  private val trainingDocs =
    for {
      tr <- training
      (classification, doc) <- tokenizer.trainingSerialize(tr)
    } yield escape(classification) + " " + doc
  private val documentSampleStream: DocumentSampleStream = new DocumentSampleStream(new PlainTextByLineStream(new
      StringReader(trainingDocs mkString "\n")))
  private val defaultFeatureGenerator = new BagOfWordsFeatureGenerator
  private val model: DoccatModel = if (trainingDocs.isEmpty) {
    println("No training docs available")
    null
  } else {
    println("Training transaction categorizer.")

    val events = new DocumentCategorizerEventStream(documentSampleStream, defaultFeatureGenerator)
    val hashSumEventStream = new HashSumEventStream(events)
    val model = opennlp.maxent.GIS.trainModel(
      /*iterations = */ 100,
      /*indexer = */ new TwoPassDataIndexer(hashSumEventStream, 5, true),
      /*printMessagesWhileTraining = */ false,
      /*smoothing = */ false,
      /*modelPrior = */ null,
      /*cutoff = */ 0,
      /*threads = */ 1)

    new DoccatModel("en", model)
  }
  private val categorizer: DocumentCategorizerME = if (model != null) new DocumentCategorizerME(model) else null

  def classify(tr: BankEtlTransaction): Classification =
    if (categorizer != null) {
      val doubles: Array[Double] = categorizer categorize (tokenizer evalSerialize tr)
      val guesses = (for ((d, i) <- doubles.toList.zipWithIndex) yield {
        Guess(unescape(categorizer.getCategory(i)), d)
      }) sortBy {
        0 - _.confidence
      }
      if (debug) println(tr)
      if (debug) println(guesses)
      Classification(guesses)
    } else {
      Classification(Nil)
    }

  private def unescape(classification: String) = {
    classification.replace("_", " ")
  }

  private def escape(classification: String) = {
    require(!classification.contains("_"), s"Accounts can't contain an '_': [$classification]") // fixme there are better ways to do this
    // . just being expedient.
    classification.replace(" ", "_")
  }
}
