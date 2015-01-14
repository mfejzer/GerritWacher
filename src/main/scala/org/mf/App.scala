package org.mf

import org.rogach.scallop._
import play.api.libs.json._

import scala.sys.process.ProcessIO

object App {

  class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {

    import scala.reflect.runtime.universe._

    def propsListConverter[A](conv: ValueConverter[A])
                             (implicit tt: TypeTag[Map[String, A]]): ValueConverter[Map[String, A]] =
      new ValueConverter[Map[String, A]] {
        val rgx = """([^=]+)=(.*)""".r

        def parse(s: List[(String, List[String])]) = {
          try {
            Right {
              val m = s.map(_._2).flatten.map(_.trim).filter("," !=).flatMap(_ split " " filter (_.trim.size > 0)).map {
                case rgx(key, value) => (key, conv.parse(List(("", List(value)))).right.get.get)
              }.toMap
              if (m.nonEmpty) Some(m)
              else None
            }
          } catch {
            case _: Throwable =>
              Left("wrong arguments format")
          }
        }

        val tag = tt
        val argType = ArgType.LIST
      }

    val propsStringListConverter = propsListConverter[List[String]](stringListConverter)

    val hostname = opt[String](required = true)
    val port = opt[String](required = true)
    val desiredReviewers = props[List[String]]('D', keyName = "project", valueName = "reviewers")(propsStringListConverter)
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)
    val hostname = conf.hostname.apply()
    val port = conf.port.apply()
    val desiredReviewersForProject = conf.desiredReviewers

    println(hostname + " " + port)

    def whatToDo(line:String) = onPachsetCreatedEvent(addReviewers(hostname, port, desiredReviewersForProject))(line)
    startListeningProcess(hostname, port, whatToDo)
  }

  def startListeningProcess(hostname: String, port: String, onEvent: String => Unit) {
    val pb = sys.process.stringSeqToProcess(Seq("ssh", "-p", port, hostname, "gerrit", "stream-events"))
    val pio = new ProcessIO(_ => (),
      stdout => scala.io.Source.fromInputStream(stdout).getLines.foreach(onEvent),
      stderr => scala.io.Source.fromInputStream(stderr).getLines.foreach(println)
    )
    pb.run(pio)
  }

  def onPachsetCreatedEvent(pachsetCreatedAction: JsValue => Unit)(line: String) = {
    val parsed = Json.parse(line)
    val t = (parsed \ "type").asOpt[String].get
    if (t.equals("""patchset-created""")) {
      pachsetCreatedAction(parsed)
    }
  }

  def addReviewers(hostname: String, port: String, projectReviewers: Map[String, List[String]])(parsedEvent: JsValue) = {
    val p = (parsedEvent \ "change" \ "project").as[String]
    val id = (parsedEvent \ "change" \ "id").as[String]
    projectReviewers.getOrElse(p, List()).foreach(reviewer => addReviewer(hostname, port, reviewer, id))
  }

  def addReviewer(hostname: String, port: String, reviewer: String, id: String): Unit = {
    println(reviewer, id)
    sys.process.stringSeqToProcess(Seq("ssh", "-p", port, hostname, "gerrit", "set-reviewers", "-a", reviewer, id)).run
  }
}