package org.vvcephei.banketl.ml

import opennlp.tools.tokenize.SimpleTokenizer
import org.vvcephei.banketl.BankEtlTransaction
import org.vvcephei.scalaledger.lib.model.Transaction

case class Serializer(sourceAccounts: Set[String]) {
  val tokenizer = SimpleTokenizer.INSTANCE

  def trainingSerialize(transaction: Transaction): Seq[(String, String)] =
    for {
      source <- transaction.postings.filter(_.isRight).map(_.right.get.account).filter(sourceAccounts.contains) //
      // only learn from the ones that we know a dest account for
      lineItem <- transaction.postings
      posting <- lineItem.right.toSeq
      destination = posting.account
      if !(sourceAccounts contains destination) // don't train on the double-entries
      quantity <- posting.quantity
      amount = quantity.amount // these _should_ always be some amount
    } yield {
      val canonical = BankEtlTransaction(
        source,
        transaction.transactionStart.date,
        amount,
        transaction.transactionStart.description ::
          transaction.transactionStart.comment.toList.map(_.comment) ++ posting.comment.toList.map(_.comment)
      )

      val tokenizedData = evalSerialize(canonical)
      (destination, tokenizedData)
    }

  def evalSerialize(transaction: BankEtlTransaction): String = {
    val tokens = (for {
      string <- transaction.description
      token <- tokenizer.tokenize(string).toList
    } yield token).mkString(" ").toLowerCase

    tokens
  }
}
