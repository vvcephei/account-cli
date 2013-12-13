package org.vvcephei.banketl.etl

import org.vvcephei.banketl.{BankEtlTransaction, LedgerTransactionMatcher, OptionsBuilder}
import org.joda.time.DateTime
import com.github.tototoshi.csv.CSVReader
import org.vvcephei.banketl.Util._
import org.vvcephei.banketl.OptionsBuilder.CsvAccount
import org.vvcephei.scalaledger.lib.model.LedgerTransaction

object EtlCsv {
  private def toDoubleAmount(amount: String, invert: Boolean) =
    amount.replaceAll("[^-.0-9]", "").toDouble * (if (invert) -1.0 else 1.0)

  //todo: use a real string similarity measure: https://github.com/rockymadden/stringmetric
  private def matchPred(line: List[String], acct: CsvAccount) = (entry: LedgerTransaction) => {
    val amount: Double = toDoubleAmount(line(acct.columns.amount), acct.invertAmount)
    val doubles: List[Double] = entry.postings filter { _.amount.isDefined } map { _.amount.get }
    val b: Boolean = ((doubles contains amount) || (doubles contains (-1.0 * amount))) &&
      (entry.description contains line(acct.columns.memo).trim)
    b
  }

  private def audit(line: List[String], matches: Seq[LedgerTransaction], empty: Boolean) =
    mapper writeValueAsString Map("line" -> line, "matches" -> !empty).++(if (empty) Seq() else Seq("entries" -> matches))

  def etl(opts: OptionsBuilder.Options, now: DateTime, matcher: LedgerTransactionMatcher): List[BankEtlTransaction] = {
    println("loading csv files...")
    var headers: Map[String, List[String]] = Map()
    val transactions: Seq[(OptionsBuilder.CsvAccount, List[List[String]])] =
      for {
        acct <- opts.banksFromCsv
        if acct.file.exists()
      } yield {
        val reader = CSVReader.open(acct.file)
        val lines: List[List[String]] = reader.all()

        if (acct.header) {
          headers = headers + (acct.ledgerAccount -> lines(0))
          acct -> lines.drop(1)
        }
        else acct -> lines
      }

    println("got %d files with %d transactions".format(transactions.size, transactions.foldLeft(0)(_ + _._2.size)))

    val filtered =
      toMultiMap(for {
        (acct, lines) <- transactions
        line <- lines
        if line.filterNot(_ == "") != Nil
        matches = matcher.matches(acct.dateFormat parseDateTime line(acct.columns.date), matchPred(line, acct))
        empty = matches.isEmpty
        _ = if (opts.verbose) println(audit(line, matches, empty)) else ()
        if empty
      } yield {
        acct -> line
      })

    println("%d new transactions".format(filtered.foldLeft(0)(_ + _._2.size)))

    for {
      (acct, lines) <- filtered.toList
      line <- lines
    } yield {
      BankEtlTransaction(
        account = acct.ledgerAccount,
        date = acct.dateFormat parseDateTime line(acct.columns.date),
        amount = toDoubleAmount(line(acct.columns.amount), acct.invertAmount),
        description = line(acct.columns.memo) :: removeAll(line, acct.columns.memo, acct.columns.date, acct.columns.amount))
    }
  }
}
