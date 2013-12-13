package org.vvcephei.banketl

import com.beust.jcommander.JCommander

import org.joda.time.DateTime
import org.vvcephei.banketl.etl.{EtlOfx, EtlCsv}
import scala.io.Source
import org.vvcephei.banketl.ui.Classifier.{Account, Skip, Quit}
import org.vvcephei.scalaledger.lib.parse.LedgerDataFileParser
import org.vvcephei.scalaledger.lib.write.LedgerDataFileWriter
import org.vvcephei.scalaledger.lib.model.{Posting, LedgerTransaction}

case class BankEtlTransaction(account: String, date: DateTime, amount: Double, description: List[String])

object ETL {
  def main(args: Array[String]) {
    new JCommander(OptionsBuilder, args: _*)
    main(OptionsBuilder.build)
  }

  def main(opts: OptionsBuilder.Options) {
    val now = DateTime.now()
    val ledger = LedgerDataFileParser parse Source.fromFile(opts.ledger).getLines()
    val matcher: LedgerTransactionMatcher = LedgerTransactionMatcher(ledger)
    val trxClassifier = ml.Classifier(ledger, opts.ledgerAccounts, opts.verbose)

    println("current time: " + now)

    val ofxs = EtlOfx.etl(opts, now, matcher)
    val csvs = EtlCsv.etl(opts, now, matcher)
    val toLoad = ofxs ::: csvs

    val withGuessedAccounts = toLoad.toStream map { tr => trxClassifier.classify(tr) -> tr }

    val ledgerWriter = LedgerDataFileWriter(opts.ledger, append = true)

    try {
      for ((guessedAcct, transaction) <- withGuessedAccounts) {
        val resp = ui.Classifier.classify(guessedAcct, transaction)
        resp match {
          case Quit() =>
            ledgerWriter.close()
            println("Exiting...")
            System.exit(0)
          case Skip() => ()
          case Account(a) => ledgerWriter.write(
            LedgerTransaction(
              date = transaction.date,
              marker = None,
              code = None,
              description = transaction.description mkString "; ",
              notes = Nil,
              postings = List(
                Posting(transaction.account, Some(transaction.amount)),
                Posting(a, Some(-1.0 * transaction.amount))
              ))
          )
        }
      }
    } finally {
      ledgerWriter.close()
      println("Exiting...")
    }
  }
}
