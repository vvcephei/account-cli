package org.vvcephei.banketl.etl

import org.vvcephei.banketl.{BankEtlTransaction, LedgerTransactionMatcher, OptionsBuilder}
import org.joda.time.DateTime
import org.vvcephei.banketl.Util._
import org.vvcephei.banketl.OptionsBuilder.OfxAccount
import org.vvcephei.scalaofx.lib.model.Account
import org.vvcephei.scalaofx.client.BankClient
import org.vvcephei.scalaledger.lib.model.LedgerTransaction
import org.vvcephei.scalaofx.lib.model.response.{BankStatementError, Transaction}

object EtlOfx {
  private def printErrors(errors: Seq[BankStatementError]) =
    if (!errors.isEmpty)
      println("Errors getting some statements: \n" + (for (err <- errors) yield { "  " + err}).mkString("\n"))
    else ()

  def etl(opts: OptionsBuilder.Options, now: DateTime, matcher: LedgerTransactionMatcher): List[BankEtlTransaction] = {
    val clients = for ((login, accounts) <- opts.banksToQuery) yield {
      BankClient(login.user, login.bank) -> accounts
    }

    println("getting statements...")

    val statements =
      for {
        (client, ofxAccounts) <- clients
        OfxAccount(ledgerName, r, a, t) <- ofxAccounts
        response = client.bankStatements(Seq(Account(r, a, t)), opts.startDate)
        _ = printErrors(response.errors)
        statement <- response.statements
      } yield {
        println("%s balance: %.2f".format(ledgerName, statement.ledgerBalance))
        ledgerName -> statement
      }

    //    println(mapper writeValueAsString statements)
    //    System.exit(0)

    println("got %d statements with %d transactions".format(statements.size, statements.map(_._2).foldLeft(0)(_ + _.transactions.size)))
    println("matching transactions...")

    def audit(acct: String, trx: Transaction, matches: Seq[LedgerTransaction], empty: Boolean) =
      mapper writeValueAsString Map("acct" -> acct, "trx" -> trx, "matches" -> !empty).++(if (empty) Seq() else Seq("entries" -> matches))

    //todo: use a real string similarity measure: https://github.com/rockymadden/stringmetric
    def matchPred(ledgerAcct: String, trx: Transaction) = (entry: LedgerTransaction) =>
      ((entry.postings map {
        _.account
      }) contains ledgerAcct) &&
        (entry.postings filter {
          _.amount.isDefined
        } map {
          _.amount.get
        }).contains(trx.amount) &&
        ((entry.description contains trx.transactionId) ||
          trx.name.isDefined && (entry.description contains trx.name.get) ||
          trx.memo.isDefined && (entry.description contains trx.memo.get))


    val statementsAndUpdates =
      toMultiMap(for {
        (ledgerAccount, statement) <- statements.toSeq
        trx <- statement.transactions
        contains: List[String] = trx.transactionId :: trx.name.toList ::: trx.memo.toList
        matches = matcher.matches(trx.posted, matchPred(ledgerAccount, trx))
        empty = matches.isEmpty
        _ = if (opts.verbose) println(audit(ledgerAccount, trx, matches, empty)) else ()
        if empty
      } yield {
        println(trx)
        ledgerAccount -> trx
      })


    println("%d new transactions".format(statementsAndUpdates.foldLeft(0)(_ + _._2.size)))

    for {
      (ledgerAccount, transactions) <- statementsAndUpdates.toList
      trx <- transactions
    } yield {
      BankEtlTransaction(
        account = ledgerAccount,
        date = trx.posted,
        amount = trx.amount,
        description = trx.name.toList ::: trx.memo.toList ::: List(trx.`type`.toString, trx.transactionId))
    }
  }
}
