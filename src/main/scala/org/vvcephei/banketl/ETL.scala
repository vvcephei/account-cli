package org.vvcephei.banketl

import java.io.{File, FileOutputStream, FileWriter, PrintWriter}

import com.beust.jcommander.JCommander
import org.joda.time.DateTime
import org.vvcephei.banketl.etl.{EtlCsv, EtlOfx}
import org.vvcephei.banketl.ui.Classifier.{Account, Quit, Skip}
import org.vvcephei.scalaledger.lib.model.{Posting, Quantity}
import org.vvcephei.scalaledger.lib.parse.LedgerParser

import scala.io.Source

case class BankEtlTransaction(account: String, date: DateTime, amount: Double, description: List[String])

object ETL {
  def main(args: Array[String]) {
    new JCommander(OptionsBuilder, args: _*)
    main(OptionsBuilder.build)
  }

  def main(opts: OptionsBuilder.Options) {
    val now = DateTime.now()
    val ledger = LedgerParser parse opts.ledger
    val extraTraining = opts.trainingLedgers flatMap { f => (LedgerParser parse f).transactions}
    val oldAndNewTransactions = ledger.transactions ++ extraTraining
    val matcher: LedgerTransactionMatcher = LedgerTransactionMatcher(oldAndNewTransactions)
    val trxClassifier = ml.Classifier(oldAndNewTransactions, opts.ledgerAccounts, opts.verbose)

    println("current time: " + now)

    val ofxs = EtlOfx.etl(opts, now, matcher)
    val csvs = EtlCsv.etl(opts, now, matcher)
    val toLoad = ofxs ::: csvs

    val withGuessedAccounts = toLoad.sortBy(_.date.toDate) map { tr => trxClassifier.classify(tr) -> tr}

    val total = withGuessedAccounts.size

    try {
      val classifier = new ui.Classifier(oldAndNewTransactions.flatMap(_.postings.filter(_.isRight).map(_.right.get.account)).toSet)
      for (((guessedAcct, transaction), index) <- withGuessedAccounts.zipWithIndex) {
        val resp = classifier.classify(guessedAcct, transaction, index, total)
        resp match {
          case Quit() =>
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
                Posting(None, transaction.account, Some(Quantity("$",transaction.amount)), None,None),
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
