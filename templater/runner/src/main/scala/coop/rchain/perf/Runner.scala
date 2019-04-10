package coop.rchain.perf

import akka.actor.ActorSystem
import com.google.protobuf.ByteString
import com.google.protobuf.empty.Empty
import coop.rchain.casper.protocol.DeployServiceGrpc.{
  DeployServiceBlockingClient,
  DeployServiceStub
}
import coop.rchain.casper.protocol._
import coop.rchain.crypto.{PrivateKey, PublicKey}
import coop.rchain.crypto.codec.Base16
import coop.rchain.crypto.hash.Blake2b256
import coop.rchain.crypto.signatures._
import coop.rchain.models.either.EitherHelper
import coop.rchain.models.{EVar, Expr, Par}
import io.gatling.commons.util.RoundRobin
import io.gatling.commons.stats.{KO, OK}
import io.gatling.core.CoreComponents
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import io.grpc.ManagedChannelBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import coop.rchain.models.either.implicits._

object Runner {}

import io.gatling.core.Predef._

object Propose {
  def propose(session: Session)(
      client: ClientWithDetails): Future[(Session, DeployServiceResponse)] = {
    val x1 = System.currentTimeMillis()
    val (cn, _): (String, String) = session("contract").as[(String, String)]
    println(
      s"starting propose of $cn on client ${client.full} session ${session.userId}")
    val r = client.client.createBlock(Empty())
    r.map { res =>
      println(
        s"finished propose of $cn on client ${client.full} session ${session.userId}, took: ${System
          .currentTimeMillis() - x1}")
      val either = res.toEither[DeployServiceResponse]
      (session, either.right.get)
    }
  }
}

object Deploy {
  private val defaultSec = PrivateKey(
    Base16.unsafeDecode(
      "b18e1d0045995ec3d010c387ccfeb984d783af8fbb0f40fa7db126d889f6dadd")
  )

  def deploy(session: Session)(
      client: ClientWithDetails): Future[(Session, DeployServiceResponse)] = {
    val (cn, contract): (String, String) = session("contract")
      .as[(String, String)]
    val x1 = System.currentTimeMillis()
    println(
      s"starting deploy of $cn on client ${client.full} session ${session.userId}")
    val d = DeployData()
      .withTimestamp(System.currentTimeMillis())
      .withTerm(contract)
      .withDeployer(ByteString.copyFrom(Ed25519.toPublic(defaultSec).bytes))
      .withPhloLimit(Integer.MAX_VALUE)
      .withPhloPrice(1)
    val r = client.client.doDeploy(sign(defaultSec, d))
    r.map { res =>
      println(
        s"finished deploy of $cn on client ${client.full} session ${session.userId}, took: ${System
          .currentTimeMillis() - x1}")
      val either = res.toEither[DeployServiceResponse]
      (session.set("client", client), either.right.get)
    }
  }

  private def fill(
      deployData: DeployData
  )(deployer: PublicKey, sig: Array[Byte], sigAlgorithm: String): DeployData =
    deployData
      .withDeployer(ByteString.copyFrom(deployer.bytes))
      .withSig(ByteString.copyFrom(sig))
      .withSigAlgorithm(sigAlgorithm)

  private def clear(deployData: DeployData): DeployData =
    fill(deployData)(PublicKey(Array.empty[Byte]), Array.empty[Byte], "")

  def sign(sec: PrivateKey,
           deployData: DeployData,
           alg: SignaturesAlg = Ed25519): DeployData = {
    val toSign = clear(deployData).toByteString.toByteArray
    val hash = Blake2b256.hash(toSign)
    val signature = alg.sign(hash, sec)

    fill(deployData)(alg.toPublic(sec), signature, alg.name)
  }
}

object GetDataFromBlock {
  def getData(dataName: String)(session: Session)(
      client: ClientWithDetails): Future[(Session, DeployServiceResponse)] = {

    val (cn, contract): (String, String) = session("contract")
      .as[(String, String)]

    val x1 = System.currentTimeMillis()

    println(
      s"getting binary data of $cn on client ${client.full} session ${session.userId}")

    val par = Par().withExprs(Seq(Expr().withGString(dataName)))
    val parData =
      client.client.listenForDataAtName(DataAtNameQuery(0, Some(par)))

    parData.map { either =>
      val res: Either[Seq[String], DeployServiceResponse] =
        either.toEither[DeployServiceResponse]

      res match {
        case Right(v) =>
          println(
            s"got binary data ${v.message.length} of $cn on client ${client.full} session ${session.userId}, took: ${System
              .currentTimeMillis() - x1}")
          (session.set("client", client),
           DeployServiceResponse("Successfully retrieved binary data"))
        case _ =>
          println(s"$res")
          (session.set("client", client),
           DeployServiceResponse("Failed to get binary data"))
      }
    }
  }
}

class RNodeRequestAction(val actionName: String,
                         val request: Session => ClientWithDetails => Future[
                           (Session, DeployServiceResponse)],
                         val statsEngine: StatsEngine,
                         val next: Action,
                         val pool: Iterator[ClientWithDetails])
    extends ExitableAction
    with NameGen {

  override def name: String = actionName

  private def requestName(cn: String, host: String) = s"$cn-$host-$name"

  def logResponse(timings: ResponseTimings,
                  ns: Session,
                  msg: String,
                  ok: Status,
                  logName: String) = {
    statsEngine.logResponse(ns,
                            logName,
                            timings,
                            ok,
                            None,
                            Some(msg),
                            List(logName))
    ns
  }

  override def execute(session: Session): Unit = recover(session) {
    val (contractName, _): (String, String) =
      session("contract").as[(String, String)]
    val client =
      session("client").asOption[ClientWithDetails].getOrElse { pool.next() }
    val start = System.currentTimeMillis()
    io.gatling.commons.validation.Success("").map { _ =>
      val r = Try { request(session)(client) }
      val timings = ResponseTimings(start, System.currentTimeMillis())

      r match {
        case Failure(exception) =>
          exception.printStackTrace()
          next ! logResponse(timings,
                             session.markAsFailed,
                             exception.getMessage,
                             KO,
                             requestName(contractName, client.host))
        case Success(future) =>
          future.onComplete {
            case Failure(exception) =>
              exception.printStackTrace()
              next ! logResponse(timings,
                                 session.markAsFailed,
                                 exception.getMessage,
                                 KO,
                                 requestName(contractName, client.host))
            case Success((ns, DeployServiceResponse(msg))) =>
              next ! logResponse(timings,
                                 ns.markAsSucceeded,
                                 msg,
                                 OK,
                                 requestName(contractName, client.host))
          }
      }
    }
  }
}

object RNodeActionDSL {
  // Note that these two actions work in tandem. a deploy will request a client and propose will utilise it.
  // if proposes and deploys don't work in a balanced way weird behaviour might be observed on rnode.
  def propose(): RNodeActionBuilder = {
    new RNodeActionBuilder {
      override val execute = Propose.propose
      override val actionName: String = "propose"
    }
  }

  def deploy(): RNodeActionBuilder = {
    new RNodeActionBuilder {
      override val execute = Deploy.deploy
      override val actionName: String = "deploy"
    }
  }

  def getDataFromBlock(dataName: String): RNodeActionBuilder = {
    new RNodeActionBuilder {
      override val execute = GetDataFromBlock.getData(dataName)
      override val actionName: String = "getData"
    }
  }
}
abstract class RNodeActionBuilder extends ActionBuilder {
  val execute: Session => ClientWithDetails => Future[(Session,
                                                       DeployServiceResponse)]
  val actionName: String

  override def build(ctx: ScenarioContext, next: Action): Action = {
    import ctx._
    val rnodeComponents =
      protocolComponentsRegistry.components(RNodeProtocol.RNodeProtocolKey)
    new RNodeRequestAction(actionName,
                           execute,
                           coreComponents.statsEngine,
                           next,
                           rnodeComponents.pool)
  }
}

case class RNodeProtocol(hosts: List[(String, Int)]) extends Protocol {}

case class ClientWithDetails(client: DeployServiceStub,
                             host: String,
                             port: Int) {
  def full = s"$host:$port"
}

object RNodeProtocol {

  def createFor(hostStrings: List[String]): RNodeProtocol = {
    val mapped = hostStrings.map { host =>
      val s = host.split(":")
      assert(s.size == 2,
             s"Invalid host string $s, expected format is address:port")
      val address = s(0)
      val port = s(1).toInt
      (address, port)

    }
    RNodeProtocol(mapped)
  }

  val RNodeProtocolKey = new ProtocolKey {
    type Protocol = RNodeProtocol
    type Components = RNodeComponents

    def protocolClass: Class[io.gatling.core.protocol.Protocol] =
      classOf[RNodeProtocol]
        .asInstanceOf[Class[io.gatling.core.protocol.Protocol]]

    def defaultProtocolValue(
        configuration: GatlingConfiguration): RNodeProtocol =
      throw new IllegalStateException(
        "Can't provide a default value for RNodeProtocol")

    def newComponents(
        system: ActorSystem,
        coreComponents: CoreComponents): RNodeProtocol => RNodeComponents = {
      rnodeProtocol =>
        {
          val clients: List[ClientWithDetails] =
            rnodeProtocol.hosts.map {
              case (host, port) =>
                val channel = ManagedChannelBuilder
                  .forAddress(host, port)
                  .usePlaintext()
                  .build
                ClientWithDetails(DeployServiceGrpc.stub(channel), host, port)
            }
          val pool = RoundRobin(clients.toIndexedSeq)
          RNodeComponents(rnodeProtocol, clients, pool)
        }
    }
  }
}

case class RNodeComponents(rnodeProtocol: RNodeProtocol,
                           clients: List[ClientWithDetails],
                           pool: Iterator[ClientWithDetails])
    extends ProtocolComponents {

  def onStart: Option[Session => Session] = {
    Some(s => {
      println("staring session")
      s
    })
  }
  def onExit: Option[Session => Unit] = {
    Some(s => {
      println("stopping session")
    })
  }
}
