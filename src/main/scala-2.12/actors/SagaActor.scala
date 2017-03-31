package actors

import akka.actor.{Actor, Props}
object SagaActor{
  def props:Props = Props(new SagaActor)
}

class SagaActor extends Actor{

  override def receive ={
    case x:Int => sender() ! x
    case y:String => sender() ! y.foldLeft(0)((a,b) => a + b)
    case z@_ => sender() ! 30
  }
}
