package org.vvcephei.banketl

import com.fasterxml.jackson.databind.{SerializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.datatype.joda.JodaModule

object Util {
  def toMultiMap[A, B](ps: Seq[(A, B)]): Map[A, Seq[B]] = ps groupBy { _._1 } mapValues { _.map(_._2) }

  def remove[A](i: Int, l: List[A]): List[A] = l.take(i) ::: l.drop(i + 1)

  def removeAll[A](l: List[A], is: Int*): List[A] = is.toList match {
    case Nil => l
    case i :: rest => removeAll(remove(i, l), rest: _*)
  }

  val mapper = new ObjectMapper() with ScalaObjectMapper {
    registerModule(DefaultScalaModule)
    registerModule(new JodaModule)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
  } writerWithDefaultPrettyPrinter()

}

