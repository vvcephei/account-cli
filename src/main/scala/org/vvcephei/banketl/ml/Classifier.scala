package org.vvcephei.banketl.ml

import opennlp.tools.doccat.{DoccatModel, DocumentSampleStream, DocumentCategorizerME}
import opennlp.tools.util.PlainTextByLineStream
import java.io.StringReader
import org.vvcephei.banketl.BankEtlTransaction
import org.vvcephei.scalaledger.lib.parse.Ledger

case class Classifier(ledger: Ledger, ledgerAccounts: Set[String], debug: Boolean = false) {
  private val tokenizer = Serializer(ledgerAccounts)

  private def escape(clas: String) = {
    require(! clas.contains("_")) // fixme there are better ways to do this. just being expedient.
    clas.replace(" ","_")
  }
  private def unescape(clas: String) = {
    clas.replace("_"," ")
  }

  private val trainingDocs =
    for {
      tr <- ledger.transactions
      (clas, doc) <- tokenizer.trainingSerialize(tr)
    } yield escape(clas) + " " + doc

  private val documentSampleStream: DocumentSampleStream = new DocumentSampleStream(new PlainTextByLineStream(new StringReader(trainingDocs mkString "\n")))
  private val model: DoccatModel = {
    println("Training transaction categorizer.")
    DocumentCategorizerME.train("en", documentSampleStream)
  }

  private val categorizer = new DocumentCategorizerME(model)

  def classify(tr: BankEtlTransaction): List[String] = {
    val doubles: Array[Double] = categorizer categorize (tokenizer evalSerialize tr)
    val categories = (for ((d, i) <- doubles.toList.zipWithIndex) yield { unescape(categorizer.getCategory(i)) -> d }) sortBy { 0 - _._2 }
    if (debug) println(tr)
    if (debug) println(categories)
    categories take 3 map { _._1 }
  }
}
