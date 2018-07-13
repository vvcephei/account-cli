package org.vvcephei.banketl

import com.beust.jcommander.JCommander
import org.joda.time.DateTime
import org.vvcephei.banketl.etl.{EtlCsv, EtlOfx}
import org.vvcephei.banketl.ml.Classification
import org.vvcephei.banketl.ui.Classifier.{Account, Quit, Skip}
import org.vvcephei.scalaledger.lib.model.{Posting, Quantity, Transaction, TransactionStart}
import org.vvcephei.scalaledger.lib.parse.LedgerParser
import org.vvcephei.scalaledger.lib.write.LedgerDataFileWriter

import scala.collection.immutable
import scala.collection.immutable.Seq

case class BankEtlTransaction(account: String, date: DateTime, amount: Double, description: List[String])

object ETL {
  def main(args: Array[String]) {
    new JCommander(OptionsBuilder, args: _*)
    main(OptionsBuilder.build)
  }

  def main(opts: OptionsBuilder.Options) {
    val now = DateTime.now()
    val ledger = LedgerParser parse opts.ledger
    val extraTraining = opts.trainingLedgers flatMap { f => (LedgerParser parse f).transactions }
    val oldAndNewTransactions = ledger.transactions ++ extraTraining
    val matcher: LedgerTransactionMatcher = LedgerTransactionMatcher(oldAndNewTransactions)
    val trxClassifier = ml.Classifier(oldAndNewTransactions, opts.ledgerAccounts, opts.verbose)

    println("current time: " + now)

    val ofxs = EtlOfx.etl(opts, now, matcher)
    val csvs = EtlCsv.etl(opts, now, matcher)
    val toLoad: Seq[BankEtlTransaction] = ofxs ::: csvs

    val withGuessedAccounts: Seq[(Classification, BankEtlTransaction)] =
      toLoad.sortBy(_.date.toDate).map(tr => trxClassifier.classify(tr) -> tr)

    val total = withGuessedAccounts.size

    val ledgerWriter = LedgerDataFileWriter(opts.ledger, append = true)

    try {
      val classifier = new ui.Classifier(
        oldAndNewTransactions.flatMap(_.postings.filter(_.isRight).map(_.right.get.account)).toSet
      )

      for (((guessedAcct, transaction), index) <- withGuessedAccounts.zipWithIndex) {
        val resp = classifier.classify(guessedAcct, transaction, index, total)
        resp match {
          case Quit() =>
            println("Exiting...")
            ledgerWriter.close()
            System.exit(0)
          case Skip() => ()
          case Account(a) => ledgerWriter.write(
            Transaction(
              comment = List(),
              transactionStart = TransactionStart(
                transaction.date,
                None,
                None,
                transaction.description.mkString("; " + ""),
                None
              ),
              postings = List(
                Right(Posting(None, transaction.account, Some(Quantity("$", transaction.amount)), None, None)),
                Right(Posting(None, a, Some(Quantity("$", -1.0 * transaction.amount)), None, None))
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
