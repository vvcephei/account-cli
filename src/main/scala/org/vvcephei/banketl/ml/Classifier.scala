package org.vvcephei.banketl.ml

import java.io.StringReader

import opennlp.model.{HashSumEventStream, TwoPassDataIndexer}
import opennlp.tools.doccat._
import opennlp.tools.util.PlainTextByLineStream
import org.vvcephei.banketl.BankEtlTransaction
import org.vvcephei.scalaledger.lib.model.LedgerTransaction


case class Guess(value: String, confidence: Double)
case class Classification(guesses: List[Guess]) {
  def top(n: Int) = guesses take n
}

case class Classifier(training: List[LedgerTransaction], ledgerAccounts: Set[String], debug: Boolean = false) {
  private val tokenizer = Serializer(ledgerAccounts)

  private def escape(clas: String) = {
    require(!clas.contains("_")) // fixme there are better ways to do this. just being expedient.
    clas.replace(" ", "_")
  }

  private def unescape(clas: String) = {
    clas.replace("_", " ")
  }

  private val trainingDocs =
    for {
      tr <- training
      (clas, doc) <- tokenizer.trainingSerialize(tr)
    } yield escape(clas) + " " + doc

  private val documentSampleStream: DocumentSampleStream = new DocumentSampleStream(new PlainTextByLineStream(new StringReader(trainingDocs mkString "\n")))
  private val defaultFeatureGenerator = new BagOfWordsFeatureGenerator
  private val model: DoccatModel = {
    println("Training transaction categorizer.")

    val events = new DocumentCategorizerEventStream(documentSampleStream, defaultFeatureGenerator)
    val hses = new HashSumEventStream(events)
    val model = opennlp.maxent.GIS.trainModel(
      /*iterations = */100,
      /*indexer = */new TwoPassDataIndexer(hses, 5, true),
      /*printMessagesWhileTraining = */false,
      /*smoothing = */false,
      /*modelPrior = */null,
      /*cutoff = */0,
      /*threads = */1)

    new DoccatModel("en", model)
  }

  private val categorizer = new DocumentCategorizerME(model)


  def classify(tr: BankEtlTransaction): Classification = {
    val doubles: Array[Double] = categorizer categorize (tokenizer evalSerialize tr)
    val guesses = (for ((d, i) <- doubles.toList.zipWithIndex) yield {
      Guess(unescape(categorizer.getCategory(i)), d)
    }) sortBy {
      0 - _.confidence
    }
    if (debug) println(tr)
    if (debug) println(guesses)
    Classification(guesses)
  }
}
