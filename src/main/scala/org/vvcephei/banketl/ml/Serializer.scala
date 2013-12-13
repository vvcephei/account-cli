package org.vvcephei.banketl.ml

import opennlp.tools.tokenize.SimpleTokenizer
import org.vvcephei.banketl.BankEtlTransaction
import org.vvcephei.scalaledger.lib.model.LedgerTransaction

case class Serializer(sourceAccounts: Set[String]) {
  val tokenizer = SimpleTokenizer.INSTANCE

  def evalSerialize(transaction: BankEtlTransaction): String =
    (transaction.account :: transaction.amount :: transaction.date.monthOfYear().getAsText :: transaction.date.dayOfMonth().getAsText :: transaction.date.dayOfWeek().getAsText ::
      (for {
        string <- transaction.description
        token <- tokenizer.tokenize(string).toList
      } yield token).toList).mkString(" ")

  def trainingSerialize(transaction: LedgerTransaction): Seq[(String, String)] =
    for {
      source <- transaction.postings map { _.account } filter sourceAccounts.contains // only learn from the ones that we know a dest account for
      posting <- transaction.postings
      if !(sourceAccounts contains posting.account) // don't train on the double-entries
      amount <- posting.amount // these _should_ always be some amount
    } yield {
      val canonical = BankEtlTransaction(source, transaction.date, amount, transaction.description :: transaction.notes ::: posting.notes)
      (posting.account, evalSerialize(canonical))
    }
}
