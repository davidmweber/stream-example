package ws

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl._
import akka.stream.{FlowShape, OverflowStrategy}
import akka.util.ByteString
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.duration._

import scala.io.StdIn

object ws2 extends App {

  implicit val actorSystem = ActorSystem("akka-system")
  implicit val flowMaterializer = ActorMaterializer()

  val interface = "localhost"
  val port = 8080

  val echoService: Flow[Message, Message, _] = Flow[Message].map {
    case TextMessage.Strict(txt) => TextMessage("ECHO: " + txt)
    case _ => TextMessage("Message type unsupported")
  }

  case class IntMessage(msg: String)

  case object ConnectionClosed

  class EchoActor extends ActorPublisher[IntMessage] with ActorLogging {

    log.debug(s"Actor created at: ${System.currentTimeMillis()}")
    println(s"Actor created at: ${System.currentTimeMillis()}")


    override def receive: Receive = idle

    def idle: Receive = {
      case "Test" => onNext(IntMessage(s"Publish: ${System.currentTimeMillis()}"))
      case IntMessage(m) ⇒
//        context.system.scheduler.schedule(0 seconds, 3 seconds, self, "Test")
        log.debug(s"Recived message: $m")
        println(s"Recived message: $m")
//        onNext(IntMessage(s"Echo: $m"))
      case ConnectionClosed =>
        log.debug("The connection was closed")
        println("The connection was closed")
      case _ =>
        log.debug("Wrong message received in idle state")
        println("Wrong message received in idle state")
    }


  }

  def actorFlow: Flow[Message, Message, Any] =
    Flow.fromGraph(

      GraphDSL.create(Source.actorRef[IntMessage](bufferSize = 5, OverflowStrategy.fail)) {
        implicit builder =>
          source =>

            import GraphDSL.Implicits._

            val d = Source.actorRef[IntMessage](bufferSize = 5, OverflowStrategy.fail)

            //input flow, all Messages
            val fromWebsocket = builder.add(
              Flow[Message].collect {
                case TextMessage.Strict(txt) => IntMessage(txt)
              })

            //output flow, it returns Message's
            val backToWebsocket = builder.add(
              Flow[IntMessage].map {
                case IntMessage(msg) => TextMessage.Strict(msg)
              }
            )

            val broker = actorSystem.actorOf(Props(classOf[EchoActor]))

            val brokerSink = Sink.actorRef(broker, ConnectionClosed)

            //Message from websocket is converted into IncommingMessage and should be send to each in room
            fromWebsocket ~> brokerSink

            //Actor already sit in chatRoom so each message from room is used as source and pushed back into websocket
            source ~> backToWebsocket

            // expose ports
            FlowShape(fromWebsocket.in, backToWebsocket.out)
      }

    )


  val route = get {
    path("ws-echo") {
      handleWebSocketMessages(echoService)
    }
    path("echoActor") {
      handleWebSocketMessages(actorFlow)
    }
  }

  val binding = Http().bindAndHandle(route, interface, port)
  println(s"Server is now online at http://$interface:$port\nPress RETURN to stop...")
  StdIn.readLine()

  import actorSystem.dispatcher

//  binding.map(_.unbind()).onComplete(_ => actorSystem.shutdown())
  println("Server is down...")
}

