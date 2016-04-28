package org.vvcephei.banketl.ui

import java.io.PrintStream

object util {
  val ANSI_RESET = "\u001B[0m"
  val ANSI_BLACK = "\u001B[30m"
  val ANSI_RED = "\u001B[31m"
  val ANSI_GREEN = "\u001B[32m"
  val ANSI_YELLOW = "\u001B[33m"
  val ANSI_BLUE = "\u001B[34m"
  val ANSI_PURPLE = "\u001B[35m"
  val ANSI_CYAN = "\u001B[36m"
  val ANSI_WHITE = "\u001B[37m"
  
  private[this] def colorln(ansi: String)(out:PrintStream)(txt:String) = {
    out.print(ansi)
    out.println(txt)
    out.print(ANSI_RESET)
  }
  
  def redln(out:PrintStream)(txt: String) = colorln(ANSI_RED)(out)(txt)
  def yellowln(out:PrintStream)(txt: String) = colorln(ANSI_YELLOW)(out)(txt)
  def greenln(out:PrintStream)(txt: String) = colorln(ANSI_GREEN)(out)(txt)
}
