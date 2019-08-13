package coop.rchain.perf

import java.nio.file.{Files, Paths}

import collection.JavaConverters._
import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef.{atOnceUsers, scenario, Simulation}
import io.gatling.core.Predef._

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.io.Source

class DeployProposeSimulation extends Simulation {
  import RNodeActionDSL._
  val defaultTerm =
    """
      |contract @"dupe"(@depth) = {
      |  if (depth <= 0) { Nil } else { @"dupe"!(depth-1) | @"dupe"!(depth-1) | @"dupe"!(depth-1) | @"dupe"!(depth-1) | @"dupe"!(depth-1) | @"dupe"!(depth-1) | @"dupe"!(depth-1) | @"dupe"!(depth-1) | @"dupe"!(depth-1) | @"dupe"!(depth-1) }
      |} |
      |@"dupe"!(3)
    """.stripMargin

  val setupWide =
    """
      |contract @"makeWide"(@n, ret) = {
      |new loop in {
      |  contract loop(@k, @acc) = {
      |    if (k == 0) { ret!(acc) }
      |    else {
      |      new name in {
      |        loop!(k - 1, {acc | for(_ <- name){ Nil } | name!(Nil)})
      |      }
      |    }
      |  } |
      |  loop!(n, Nil)
      |}
      |} |
      |@"makeWide"!(500, "myWide")
    """.stripMargin

  val runWide = 
    """
      |for(@wide <- @"myWide") {
      |  wide //execute wide processes
      |}
    """.stripMargin

  val conf   = ConfigFactory.load()
  val rnodes = conf.getStringList("rnodes").asScala.toList

  val contracts = sys.props
    .get("contract")
    .map(
      path =>
        Paths.get(path) match {
          case p if Files.isDirectory(p) => ContinuousRunner.getAllRhosFromPath(p)
          case p =>
            List((p.getFileName.toString, Source.fromFile(p.toUri).mkString))
        }
    )
    .getOrElse(List(("a-setup-wide", setupWide), ("b-run-wide", runWide)))

  println(s"will run simulation on ${rnodes.mkString(", ")}, contracts:")
  println("-------------------------------")
  println(contracts)
  println("-------------------------------")

  val protocol = RNodeProtocol.createFor(rnodes)

  val scn = scenario("DeployProposeSimulation")
    .foreach(contracts, "contract") {
      repeat(1) {
        repeat(1) {
          exec(deploy())
        }.exec(propose())
      }
    }

  setUp(
    scn.inject(rampUsers(1) over (5 seconds))
  ).protocols(protocol)
}
