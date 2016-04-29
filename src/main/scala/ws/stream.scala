/*
 * Copyright © ${year} 8eo Inc.
 */
package ws

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ThrottleMode.Shaping
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.duration._

object stream extends App {
  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val source: Source[Int, NotUsed] = Source(1 to 100)
  val sink = Sink.fold[Int,Int](0)(_ + _)
  val flow = Flow[Int].map(_ + 0).throttle(15, 1 second, 25, Shaping)

  val r = source.via(flow).toMat(sink)(Keep.right)
  println("start")
  val f = r.run().onComplete(println(_))

  //source.runForeach(i => println(i))(materializer)

}
