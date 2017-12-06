package org.vvcephei.banketl

import org.joda.time.DateTime
import org.vvcephei.scalaledger.lib.model.Transaction
import org.vvcephei.scalaledger.lib.parse.LedgerParser
import org.vvcephei.scalaofx.lib.message

import scala.collection.immutable

case class LedgerTransactionMatcher(entries: List[Transaction]) {
  private val byDate = entries groupBy {
    t => message.Util.toDateString(t.transactionStart.date)
  }

  def matches(targetDate: DateTime, pred: Transaction => Boolean): immutable.IndexedSeq[Transaction] =
    for {
      d <- (-1 to 1) map targetDate.minusDays
      entry <- byDate.getOrElse(message.Util.toDateString(d), Nil)
      if pred(entry)
    } yield {
      entry
    }
}

object LedgerTransactionMatcher {
  def main(args: Array[String]) {
    val ledger = LedgerParser.parse(args(0))
    val matches = LedgerTransactionMatcher(ledger.transactions).entries sortWith {
      (p1, p2) => p1.transactionStart.date isBefore p2.transactionStart.date
    }
    matches foreach println
  }
}
