package org.vvcephei.banketl

import org.joda.time.DateTime
import scala.io.Source
import org.vvcephei.scalaledger.lib.parse.{LedgerDataFileParser, Ledger}
import org.vvcephei.scalaledger.lib.model.LedgerTransaction
import org.vvcephei.scalaofx.lib.message

case class LedgerTransactionMatcher(ledger: Ledger) {
  private val parsedLedger = ledger.transactions
  private val byDate = parsedLedger groupBy {
    t => message.Util.toDateString(t.date)
  }

  def entries = parsedLedger

  def matches(targetDate: DateTime, pred: LedgerTransaction => Boolean) =
    for {
      d <- (-5 to 5) map targetDate.minusDays
      entry <- byDate.getOrElse(message.Util.toDateString(d), Nil)
      if pred(entry)
    } yield {
      entry
    }
}

object LedgerTransactionMatcher {
  def main(args: Array[String]) {
    val ledger = LedgerDataFileParser parse Source.fromFile(args(0)).getLines()
    val matches = LedgerTransactionMatcher(ledger).parsedLedger sortWith {
      (p1, p2) => p1.date isBefore p2.date
    }
    matches foreach println
  }
}
