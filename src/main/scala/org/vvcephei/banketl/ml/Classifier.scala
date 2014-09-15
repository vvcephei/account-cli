package org.vvcephei.banketl.ml

import java.util

import opennlp.model.{HashSumEventStream, TwoPassDataIndexer, TrainUtil, AbstractModel}
import opennlp.tools.doccat._
import opennlp.tools.util.{ObjectStream, TrainingParameters, PlainTextByLineStream}
import java.io.StringReader
import opennlp.tools.util.model.ModelUtil
import org.vvcephei.banketl.BankEtlTransaction
import org.vvcephei.scalaledger.lib.model.LedgerTransaction

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

  def classify(tr: BankEtlTransaction): List[String] = {
    val doubles: Array[Double] = categorizer categorize (tokenizer evalSerialize tr)
    val categories = (for ((d, i) <- doubles.toList.zipWithIndex) yield {
      unescape(categorizer.getCategory(i)) -> d
    }) sortBy {
      0 - _._2
    }
    if (debug) println(tr)
    if (debug) println(categories)
    categories take 3 map {
      _._1
    }
  }
}
