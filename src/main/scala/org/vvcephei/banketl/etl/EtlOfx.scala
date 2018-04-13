package org.vvcephei.banketl.etl

import java.io.File
import java.nio.charset.CodingErrorAction

import org.apache.commons.io.FileUtils
import org.joda.time.DateTime
import org.vvcephei.banketl.OptionsBuilder.{FileOfxAccount, Login, WebOfxAccount}
import org.vvcephei.banketl.Util._
import org.vvcephei.banketl.{BankEtlTransaction, LedgerTransactionMatcher, OptionsBuilder}
import org.vvcephei.scalaofx.client.{BankClient, SourceClient}
import org.vvcephei.scalaofx.lib.model.Account
import org.vvcephei.scalaofx.lib.model.response.{BankStatement, BankStatementError, BankStatementResponse}

import scala.collection.JavaConverters.asScalaIterator
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

object EtlOfx {
  def etl(opts: OptionsBuilder.Options, now: DateTime, matcher: LedgerTransactionMatcher): List[BankEtlTransaction] = {

    println("getting statements...")

    val webStatements = statementsFromWeb(opts.banksToQuery, opts.startDate)
    val fileStatements = statementsFromFile(opts.banksFromOfxFile)
    val statements = webStatements ++ fileStatements

    val maxNameLength = statements.map(_._1.length).max

    for ((name, statement) <- statements.sortBy {
      case (name, stmt) => (name, stmt.endTime.getOrElse(new DateTime()).getMillis, stmt.startTime.getOrElse(new
          DateTime()).getMillis)
    }) {
      println(statementSummary(s"%-${maxNameLength}s".format(name), statement))
    }

    println("got %d statements with %d transactions".format(statements.size, statements.map(_._2).foldLeft(0)(_ + _
      .transactions.size)))
    println("matching transactions...")

    def audit(acct: String, trx: org.vvcephei.scalaofx.lib.model.response.Transaction, matches: Seq[org.vvcephei
    .scalaledger.lib.model.Transaction], empty: Boolean) =
      mapper writeValueAsString Map("acct" -> acct, "trx" -> trx, "matches" -> !empty).++(if (empty) Seq() else Seq
      ("entries" -> matches))

    //todo: use a real string similarity measure: https://github.com/rockymadden/stringmetric
    def matchPred(ledgerAcct: String, trx: org.vvcephei.scalaofx.lib.model.response.Transaction) = (entry: org
    .vvcephei.scalaledger.lib.model.Transaction) =>
      (entry.postings.flatMap(_.right.toSeq).map(_.account) contains ledgerAcct) &&
        entry.postings.flatMap(_.right.toSeq).flatMap(_.quantity).map(_.amount).contains(trx.amount) &&
        ((entry.transactionStart.description contains trx.transactionId) ||
          trx.name.isDefined && (entry.transactionStart.description contains trx.name.get) ||
          trx.memo.isDefined && (entry.transactionStart.description contains trx.memo.get))


    val statementsAndUpdates =
      toMultiMap(for {
        (ledgerAccount, statement) <- statements
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

  private[this] def statementsFromFile(files: Seq[FileOfxAccount]): Seq[(String, BankStatement)] = {
    implicit val codec: Codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    for {
      FileOfxAccount(ledgerName, inode) <- files
      _ = println(s"getting statements for $ledgerName")
      file <- toFiles(inode)
      _ = println(s"reading ${file.getAbsolutePath}")
      loaded = SourceClient.bankStatements(Source.fromFile(file))
      _ = printErrors(loaded.errors)
      statement <- loaded.statements
    } yield {
      (ledgerName, statement)
    }
  }

  private[this] def toFiles(inode: File): Seq[File] = {
    if (inode.isFile) Seq(inode)
    else if (inode.isDirectory) asScalaIterator(FileUtils.iterateFiles(inode, null, true)).toSeq
    else if (!inode.exists()) {
      println(s"warning: file ${inode.getAbsolutePath} doesn't exist. skipping...")
      Seq()
    } else throw new IllegalArgumentException(s"${inode.getName} is not a file or directory")
  }

  private[this] def statementsFromWeb(banksToQuery: Map[Login, Seq[WebOfxAccount]], startDate: DateTime):
  Seq[(String, BankStatement)] = {
    val clients = for ((login, accounts) <- banksToQuery) yield {
      BankClient(login.user, login.bank, debug = login.debug) -> accounts
    }
    for {
      (client, ofxAccounts) <- clients.toSeq
      WebOfxAccount(ledgerName, r, a, t) <- ofxAccounts
      _ = println(s"getting statements for $ledgerName")
      response = Try(client.bankStatements(Seq(Account(Some(r), Some(a), Some(t))), startDate)) match {
        case Success(innerResp) => innerResp
        case Failure(e) =>
          BankStatementResponse(Seq(), Seq(BankStatementError("err", "err", e.getMessage)))
      }
      _ = printErrors(response.errors)
      statement <- response.statements
    } yield {
      (ledgerName, statement)
    }
  }

  private def printErrors(errors: Seq[BankStatementError]): Unit =
    if (errors.nonEmpty)
      println("Errors getting some statements: \n" + (for (err <- errors) yield {
        "  " + err
      }).mkString("\n"))
    else ()

  private[this] def statementSummary(ledgerName: String, statement: BankStatement): String =
    f"$ledgerName ${string(statement.startTime)} to ${string(statement.endTime)} balance: ${
      statement
        .ledgerBalance
    }%.2f"


  private[this] def string(odt: Option[DateTime]) = (for (dt <- odt) yield dt.toString("yyyy-MM-dd")) getOrElse "?"
}