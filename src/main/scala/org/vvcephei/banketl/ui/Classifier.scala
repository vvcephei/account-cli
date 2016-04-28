package org.vvcephei.banketl.ui

import java.io.{File, FileOutputStream, PrintWriter}

import jline.console.ConsoleReader
import jline.console.completer.{Completer, StringsCompleter}
import jline.internal.Preconditions._
import org.vvcephei.banketl.BankEtlTransaction
import org.joda.time.format.{DateTimeFormatter, DateTimeFormat}
import org.joda.time.DateTime
import org.vvcephei.banketl.ml.{Guess, Classification}
import scala.collection.JavaConversions._

object Classifier {

  sealed trait Result

  case class Quit() extends Result

  case class Skip() extends Result

  case class Account(a: String) extends Result

}

class Classifier(accounts: Set[String]) {

  import Classifier._

  private[this] val _learnLogFile = new PrintWriter(new FileOutputStream(new File("classify.log"), true))

  private[this] def learnLog(transaction: BankEtlTransaction, guesses: Seq[Guess], answer: Guess): Unit = {
    val right = answer == guesses.head
    _learnLogFile.println(s"${DateTime.now()}\t${if (right) "RIGHT" else "WRONG"}\t$transaction\t$guesses\t$answer")
    _learnLogFile.flush()
  }

  private lazy val dateTimeFormatter: DateTimeFormatter = DateTimeFormat.forPattern("YYYY/MM/dd")

  private def dateFormatter(dt: DateTime) = dateTimeFormatter.print(dt)

  private def moneyFormatter(amt: Double) = "$%.2f".format(amt).replace("$-", "-$")

  private def descriptionFormatter(ss: List[String]) = ss.mkString("; ")

  private def bar(length: Int) = "-" * length

  private def transactionFormatter(t: BankEtlTransaction, index: Int, total: Int) = {
    val d = dateFormatter(t.date)
    val a = moneyFormatter(t.amount)
    val s = descriptionFormatter(t.description)
    val indexStr = s"${index + 1}/$total"
    val border = """+-%s-+-%s-+-%s-+-%s-+-%s-+""".format(bar(indexStr.length), bar(t.account.length), bar(d.length), bar(a.length), bar(s.length))
    val body = """| %s | %s | %s | %s | %s |""" format(indexStr, t.account, d, a, s)
    border + "\n" + body + "\n" + border
  }

  private def question(amount: Double) =
    if (amount < 0) "To which account did this money go?"
    else "From which account did this money come?"

  private def menu(accounts: List[String]) = "([account] / [q]uit/ [s]kip / %s)".format(accounts.zipWithIndex map { p => "[" + (p._2 + 1) + "]" + p._1} mkString " / ")

  private val console: ConsoleReader = {
    val c = new ConsoleReader()
    c.addCompleter(new StringsCompleter(accounts))
    c
  }

  private case class ClassificatonCompleter(classification: Classification) extends Completer {
    override def complete(buffer: String, cursor: Int, candidates: java.util.List[CharSequence]) = {
      checkNotNull(candidates)
      val strings = classification.guesses.map(_.value)
      if (buffer == null) {
        candidates.addAll(strings)
      } else {
        val toAdd =
          for {
            candidate <- strings
            if candidate.toLowerCase.startsWith(buffer.toLowerCase)
          } yield candidate
        candidates.addAll(toAdd)
      }

      if (candidates.size == 1) {
        candidates.set(0, candidates.get(0) + " ")
      }

      if (candidates.isEmpty) -1 else 0
    }
  }

  def classify(classification: Classification, transaction: BankEtlTransaction, index: Int, total: Int): Result = {
    setClassification(classification)

    println("\n")
    if (transaction.amount < 0) {
      util.yellowln(System.out)(transactionFormatter(transaction, index, total))
    } else if (transaction.amount > 0) {
      util.greenln(System.out)(transactionFormatter(transaction, index, total))
    } else {
      println(transactionFormatter(transaction, index, total))
    }
    val topGuesses: List[Guess] = classification.top(3)
    val resp = console.readLine(s"${question(transaction.amount)} ${menu(topGuesses.map(_.value))} ").trim
    resp match {
      case "q" => Quit()
      case "s" => Skip()
      case "" =>
        learnLog(transaction, topGuesses, topGuesses.head)
        Account(classification.guesses.head.value)
      case n if n forall Character.isDigit =>
        val int: Int = n.toInt
        require(int > 0 && int <= classification.guesses.length, "Number was out of range")
        val answer: Guess = classification.guesses(int - 1)
        learnLog(transaction, topGuesses, answer)
        Account(answer.value)
      case s =>
        val maybeAnswer: Option[Guess] = classification.guesses.find(_.value == s)
        if (maybeAnswer.isDefined) {
          learnLog(transaction, topGuesses, maybeAnswer.get)
        }
        Account(s)
    }
  }

  private[this] def setClassification(classification: Classification) {
    for (compl <- console.getCompleters) {
      console.removeCompleter(compl)
    }
    console.addCompleter(ClassificatonCompleter(classification))
    assert(console.getCompleters.size() == 1)
  }
}
