import sbtassembly.Plugin.AssemblyKeys
import AssemblyKeys._

name := "account-cli"

version := "1.3"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
    "org.vvcephei" %% "scala-ofx" % "1.2-SNAPSHOT",
    "org.vvcephei" %% "scala-ledger" % "1.0",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % "2.2.3",
    "org.joda" % "joda-convert" % "1.2",
    "joda-time" % "joda-time" % "2.3",
    "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.2.3",
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.1.3",
    "com.github.tototoshi" %% "scala-csv" % "0.8.0",
    "com.beust" % "jcommander" % "1.30",
    "org.apache.opennlp" % "opennlp-tools" % "1.5.3",
    "org.apache.opennlp" % "opennlp-maxent" % "3.0.3",
    "commons-io" % "commons-io" % "2.4"
)

libraryDependencies += "org.testng" % "testng" % "6.8" % "test"

mainClass := Some("org.vvcephei.banketl.ETL")

net.virtualvoid.sbt.graph.Plugin.graphSettings

assemblySettings

mainClass in assembly := Some("org.vvcephei.banketl.ETL")

