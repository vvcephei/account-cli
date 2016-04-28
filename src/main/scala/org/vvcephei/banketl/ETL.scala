package org.vvcephei.banketl

import java.io.{File, FileOutputStream, PrintWriter}

import com.beust.jcommander.JCommander
import org.joda.time.DateTime
import org.vvcephei.banketl.etl.{EtlCsv, EtlOfx}
import org.vvcephei.banketl.ui.Classifier.{Account, Quit, Skip}
import org.vvcephei.scalaledger.lib.model.{LedgerTransaction, Posting}
import org.vvcephei.scalaledger.lib.parse.LedgerDataFileParser
import org.vvcephei.scalaledger.lib.write.LedgerDataFileWriter

import scala.io.Source

case class BankEtlTransaction(account: String, date: DateTime, amount: Double, description: List[String])

object ETL {
  def main(args: Array[String]) {
    new JCommander(OptionsBuilder, args: _*)
    main(OptionsBuilder.build)
  }

  def main(opts: OptionsBuilder.Options) {
    val now = DateTime.now()
    val ledger = LedgerDataFileParser parse Source.fromFile(opts.ledger).getLines()
    val extraTraining = opts.trainingLedgers flatMap { f => (LedgerDataFileParser parse Source.fromFile(f).getLines()).transactions}
    val oldAndNewTransactions = ledger.transactions ++ extraTraining
    val matcher: LedgerTransactionMatcher = LedgerTransactionMatcher(oldAndNewTransactions)
    val trxClassifier = ml.Classifier(oldAndNewTransactions, opts.ledgerAccounts, opts.verbose)

    println("current time: " + now)

    val ofxs = EtlOfx.etl(opts, now, matcher)
    val csvs = EtlCsv.etl(opts, now, matcher)
    val toLoad = ofxs ::: csvs

    val withGuessedAccounts = toLoad.sortBy(_.date.toDate) map { tr => trxClassifier.classify(tr) -> tr}

    val ledgerWriter = LedgerDataFileWriter(opts.ledger, append = true)

    val total = withGuessedAccounts.size

    try {
      val classifier = new ui.Classifier(oldAndNewTransactions.flatMap(_.postings.map(_.account)).toSet)
      for (((guessedAcct, transaction), index) <- withGuessedAccounts.zipWithIndex) {
        val resp = classifier.classify(guessedAcct, transaction, index, total)
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

      ledgerWriter.close()

      if (opts.jsonFileOut.isDefined){
        // reload ledger:
        val ledger = (LedgerDataFileParser parse Source.fromFile(opts.ledger).getLines()).transactions ++ extraTraining
        val sorted = ledger.sortBy(_.date.toDate)
        val str = Util.mapper.withDefaultPrettyPrinter.writeValueAsString(sorted)
        val writer: PrintWriter = new PrintWriter(new FileOutputStream(opts.jsonFileOut.get))
        writer.write(str)
        writer.close()
      }
    } finally {
      ledgerWriter.close()
      println("Exiting...")
    }
  }
}
