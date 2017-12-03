name := "account-cli"

version := "1.3"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
    "org.vvcephei" %% "scala-ofx" % "1.3",
    "org.vvcephei" %% "scala-ledger" % "2.1",
    "org.joda" % "joda-convert" % "1.2",
    "joda-time" % "joda-time" % "2.4",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.2",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % "2.9.2",
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.9.2",
    "com.github.tototoshi" %% "scala-csv" % "1.3.5",
    "com.beust" % "jcommander" % "1.72",
    "org.apache.opennlp" % "opennlp-tools" % "1.8.3",
    "org.apache.opennlp" % "opennlp-maxent" % "3.0.3",
    "commons-io" % "commons-io" % "2.4",
    "jline" % "jline" % "2.12",
    "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.9"
)

libraryDependencies += "org.testng" % "testng" % "6.8" % "test"

mainClass := Some("org.vvcephei.banketl.ETL")

mainClass in assembly := Some("org.vvcephei.banketl.ETL")

