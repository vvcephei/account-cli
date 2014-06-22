package org.vvcephei.banketl.ui

import org.vvcephei.banketl.BankEtlTransaction
import org.joda.time.format.{DateTimeFormatter, DateTimeFormat}
import org.joda.time.DateTime

object Classifier {

  sealed trait Result

  case class Quit() extends Result

  case class Skip() extends Result

  case class Account(a: String) extends Result


  private lazy val dateTimeFormatter: DateTimeFormatter = DateTimeFormat.forPattern("YYYY/MM/dd")

  private def dateFormatter(dt: DateTime) = dateTimeFormatter.print(dt)

  private def moneyFormatter(amt: Double) = "$%.2f".format(amt).replace("$-", "-$")

  private def descriptionFormatter(ss: List[String]) = ss.mkString("; ")

  private def bar(length: Int) = "-" * length

  private def transactionFormatter(t: BankEtlTransaction, index: Int, total: Int) = {
    val d = dateFormatter(t.date)
    val a = moneyFormatter(t.amount)
    val s = descriptionFormatter(t.description)
    val indexStr = s"${index+1}/$total"
    val border = """+-%s-+-%s-+-%s-+-%s-+-%s-+""".format(bar(indexStr.length), bar(t.account.length), bar(d.length), bar(a.length), bar(s.length))
    val body =   """| %s | %s | %s | %s | %s |""" format(indexStr, t.account, d, a, s)
    border + "\n" + body + "\n" + border
  }

  private def question(amount: Double) =
    if (amount < 0) "To which account did this money go?"
    else "From which account did this money come?"

  private def menu(accounts: List[String]) = "([account] / [q]uit/ [s]kip / %s)".format(accounts.zipWithIndex map { p => "[" + (p._2 + 1) + "]" + p._1 } mkString " / ")

  def classify(guesses: List[String], transaction: BankEtlTransaction, index: Int, total: Int): Result = {
    println("\n")
    println(transactionFormatter(transaction, index, total))
    val resp = readLine(question(transaction.amount) + " " + menu(guesses) + " ").trim
    resp match {
      case "q" => Quit()
      case "s" => Skip()
      case "" => Account(guesses(0))
      case n if n forall Character.isDigit =>
        val int: Int = n.toInt
        require(int > 0 && int <= guesses.length, "Number was out of range")
        Account(guesses(int - 1))
      case s => Account(s)
    }
  }
}
