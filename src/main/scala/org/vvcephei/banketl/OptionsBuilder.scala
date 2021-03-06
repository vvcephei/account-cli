package org.vvcephei.banketl

import com.fasterxml.jackson.databind.{SerializationFeature, ObjectMapper}
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.beust.jcommander.Parameter
import org.joda.time.format.{DateTimeFormatter, DateTimeFormat}
import org.joda.time.DateTime
import java.io.File
import scala.collection.JavaConversions._
import org.vvcephei.banketl.Util._
import org.vvcephei.scalaofx.lib.model.{User, AccountType, Bank}

object OptionsBuilder {
  private var knownBanks = Map[String, Bank]()

  val yamlMapper = new ObjectMapper(new YAMLFactory) with ScalaObjectMapper {
    registerModule(DefaultScalaModule)
    registerModule(new JodaModule)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
  }

  @Parameter(names = Array("-v", "--verbose"), description = "Prints lots of info about transactions")
  var verbose: Boolean = false

  @Parameter(names = Array("-c", "--config"), description = "config file")
  var configArg: String = null

  @Parameter(names = Array("-o", "--output-dir"), description = "directory in which to place ")
  var outputArg: String = null

  @Parameter(names = Array("-b", "--bank"), description = "bankName:user:pass")
  var banks: java.util.List[String] = Nil

  @Parameter(names = Array("-a", "--account"), description = "bankName:routing:accountNumber:accountType")
  var accounts: java.util.List[String] = Nil

  private val pattern = DateTimeFormat.forPattern("YYYY-MM-dd")
  @Parameter(names = Array("-s", "--start-date"), description = "YYYY-MM-dd start date for transactions (default = now - 30 days)")
  var startDate: String = pattern.print(DateTime.now minusDays 30)

  @Parameter(names = Array("-d", "--days"), description = "days to get transactions for")
  var days: Int = -1

  @Parameter(names = Array("-l", "--ledger"), description = "the ledger file")
  var ledger: String = null

  @Parameter(names = Array("-t", "--training-ledger"), description = "ledger files to learn from")
  var training: java.util.List[String] = Nil

  @Parameter(names = Array("--jsonOut"), description = "also dump the ledgers to a file")
  var jsonFileOut: String = null

  case class WebOfxAccount(ledgerAccount: String, routing: String, account: String, `type`: AccountType)

  case class CsvAccountColumns(date: Int, amount: Int, memo: Int)

  case class CsvAccount(ledgerAccount: String, file: File, header: Boolean, dateFormat: DateTimeFormatter, columns: CsvAccountColumns, invertAmount: Boolean)

  case class Config(ofx: Option[Map[String, Bank]] = None,
                    logins: Option[Map[String, Map[String,String]]] = None,
                    accounts: Option[List[Map[String, String]]] = None)

  case class Login(user: User, bank: Bank, debug: Boolean = false)

  case class Options(banksToQuery: Map[Login, Seq[WebOfxAccount]] = Map() withDefaultValue Nil,
                     banksFromCsv: Seq[CsvAccount] = Seq(),
                     banksFromOfxFile: Seq[FileOfxAccount] = Seq(),
                     startDate: DateTime,
                     ledger: File,
                     trainingLedgers: List[File],
                     ledgerAccounts: Set[String],
                     outputDir: File,
                     verbose: Boolean,
                     jsonFileOut: Option[File])

  private def bankAccountsToDownload(conf: Config): Map[Login, Seq[WebOfxAccount]] = {
    val configuredLogins =
      (for {
        logins <- conf.logins.toSeq
        (b, u) <- logins.toSeq
      } yield {
        //noinspection MapGetOrElseBoolean
        b -> Login(User(u("id"),u("password")), knownBanks(b), debug = u.get("debug").map(_.toBoolean).getOrElse(false))
      }).toMap

    val argLogins = (for (bank <- banks) yield {
      bank.split(':').toList match {
        case b :: u :: p :: Nil => b -> Login(User(u, p), knownBanks(b))
        case _ => throw new IllegalArgumentException
      }
    }).toMap

    val logins = configuredLogins ++ argLogins

    val argLoginToAccounts: Map[Login, Seq[WebOfxAccount]] = toMultiMap(for (account <- accounts) yield {
      account.split(':').toList match {
        case b :: r :: a :: t :: ledgerName => logins(b) -> WebOfxAccount(ledgerName mkString ":", r, a, AccountType.from(t))
        case _ => throw new IllegalArgumentException
      }
    })

    val configuredLoginToAccounts =
      toMultiMap(for {
        accounts <- conf.accounts.toSeq
        account <- accounts.toSeq
        b <- account.get("ofx")
        ledgerName <- account.get("ledgerName")
        r <- account.get("routing")
        a <- account.get("account")
        t <- account.get("type")
      } yield {
        logins(b) -> WebOfxAccount(ledgerName, r, a, AccountType.from(t))
      })

    configuredLoginToAccounts ++ argLoginToAccounts
  }


  private def bankAccountsToLoadCsv(conf: Config) =
    for {
      accounts <- conf.accounts.toSeq
      account <- accounts.toSeq
      ledgerName <- account.get("ledgerName")
      csv <- account.get("csv")
      header <- account.get("header")
      dateC <- account.get("dateCol")
      dateFormat <- account.get("dateFormat")
      amountC <- account.get("amountCol")
      invertC <- account.get("invertAmount")
      memoC <- account.get("memoCol")
    } yield {
      CsvAccount(ledgerName, new File(csv), header.toBoolean, DateTimeFormat.forPattern(dateFormat), CsvAccountColumns(dateC.toInt, amountC.toInt, memoC.toInt), invertC.toBoolean)
    }

  case class FileOfxAccount(ledgerAccount: String, file: File)

  private def bankAccountsToLoadOfx(conf: Config) = for {
    accounts <- conf.accounts.toSeq
    account <- accounts.toSeq
    ledgerName <- account.get("ledgerName")
    ofx <- account.get("ofxFile")
  } yield FileOfxAccount(ledgerName, new File(ofx))

  def build = {
    val config =
      if (configArg != null) yamlMapper.readValue[Config](new File(configArg))
      else Config()

    for (o <- config.ofx) {
      knownBanks = knownBanks ++ o
    }

    val download = bankAccountsToDownload(config)
    val loadCsv: Seq[CsvAccount] = bankAccountsToLoadCsv(config)
    val loadOfx = bankAccountsToLoadOfx(config)

    val od = if (outputArg == null) new File(".") else new File(outputArg)
    if (!od.exists()) od.mkdirs()
    if (!od.isDirectory) throw new IllegalArgumentException("output-dir is not a directory")

    val ss: Seq[String] = download.toSeq flatMap { _._2 } map { _.ledgerAccount }
    val ss1: Seq[String] = loadCsv.toSeq map { _.ledgerAccount }
    val ss2: Seq[String] = loadOfx.toSeq map { _.ledgerAccount }

    val ledgerAccounts: Seq[String] = ss ++ ss1 ++ ss2

    val startDate: DateTime =
      if (days < 0) pattern parseDateTime this.startDate
      else new DateTime().withMillisOfDay(0).minusDays(days)
    Options(
      banksToQuery = download,
      banksFromCsv = loadCsv,
      banksFromOfxFile = loadOfx,
      startDate = startDate,
      ledger = new File(ledger),
      trainingLedgers = training.toList map {s => new File(s)},
      ledgerAccounts = ledgerAccounts.toSet,
      outputDir = od,
      verbose = verbose,
      jsonFileOut = Option(jsonFileOut).map(f => new File(f))
    )
  }

}
