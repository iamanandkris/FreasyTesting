package another

import actors.SagaActor
import akka.actor.ActorSystem
import akka.util.Timeout
import cats.{Monad, ~>}
import cats.data.Coproduct
import cats.free.Free
import freasymonad.cats.free

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

object FreasyFuture extends App{
  implicit val futureMonad = new Monad[Future] {
    override def flatMap[A, B](fa: Future[A])(f: (A) => Future[B]): Future[B] = fa.flatMap(f)
    override def tailRecM[A, B](a: A)(f: (A) => Future[Either[A, B]]): Future[B] = {
      f(a).flatMap(_ match {
        case Right(eb) => Future.successful(eb)
        case Left(ea) => tailRecM(ea)(f)
      })
    }
    override def pure[A](x: A): Future[A] = Future.successful(x)
    def ap[A, B](fa: Future[A])(f: Future[(A) => B]): Future[B] =
      fa.flatMap(a => f.map(ff => ff(a)))
  }

  @free trait Arithmetic{
    type ArithmeticAlgebra[A] = Free[Adt,A]
    sealed trait Adt[A]
    def add(x: Int, y: Int) : ArithmeticAlgebra[Int]
    def sub(x: Int, y: Int) : ArithmeticAlgebra[Int]
  }

  @free trait Saga{
    type SagaAlgebra[A] = Free[Adt,A]
    sealed trait Adt[A]
    def invokeSaga[A,B](x:A):SagaAlgebra[B]
  }

  @free trait DBOperation{
    type DatabaseAlgebra[A] = Free[Adt,A]
    sealed trait Adt[A]
    def getFromDB(key:String):DatabaseAlgebra[Int]
    def putToDB(key:String, value:Int):DatabaseAlgebra[Unit]
  }


  type Expr1[A] = Coproduct[Arithmetic.Adt, DBOperation.Adt, A]
  type Expr[A] = Coproduct[Saga.Adt, Expr1, A]
  type ResultF[A] = Future[A]

  object ArithmeticFutureInterpreter extends Arithmetic.Interp[ResultF]{
    override def add(x: Int, y: Int): ResultF[Int] = Future{x + y}
    override def sub(x: Int, y: Int): ResultF[Int] = Future{x - y}
  }

  object DatabaseFutureInterpreter extends DBOperation.Interp[ResultF]{
    val mapdata = mutable.Map.empty[String, Int]
    mapdata+=("test1" -> 100)
    mapdata+=("test4" -> 200)

    override def getFromDB(key: String): ResultF[Int] = Future{mapdata.getOrElse(key, 0)}
    override def putToDB(key: String, value: Int): ResultF[Unit] = Future{mapdata+(key -> value); ()}
  }

  object SagaFutureInterpreter extends Saga.Interp[ResultF]{
    import akka.pattern.ask

    import scala.concurrent.duration._
    implicit val timeout = Timeout(5 seconds)

    override def invokeSaga[A, B](x: A): ResultF[B] = {
      val actorSystem = ActorSystem("testActorsystem")
      val actor = actorSystem.actorOf(SagaActor.props)
      (actor ? x).map(x => x.asInstanceOf[B])
    }
  }

  val interpreter1: Expr1 ~> ResultF = ArithmeticFutureInterpreter.interpreter or DatabaseFutureInterpreter.interpreter
  val interpreter:Expr ~> ResultF = SagaFutureInterpreter.interpreter or interpreter1

  def getValidatedResult(first:Int,second:Int):Boolean = first < second

  def liftToFree[T[?],B](processMessageResult:B):Free[T, B] =
    Free.pure[T, B] (processMessageResult)


  def ifF[T[?],B](condition : => Boolean)(left : => Free[T, B])(right : => B):Free[T, B] =
    if (condition) left else liftToFree(right)


  implicit def lift[T[?],B](value: => B): Free[T, B] ={
    Free.pure[T,B](value)
  }

  /*def processAPIRequest(implicit arith:Arithmetic.Injects[Expr], dbo:DBOperation.Injects[Expr], sagaOp:Saga.Injects[Expr]): Free[Expr, Int] = {
    import arith._
    import dbo._
    import sagaOp._

    val parallelDBResult1 = getFromDB("test1")
    val parallelDBResult2 = getFromDB("test4")

    for {
      first   <- parallelDBResult1                  // Parallel
      second  <- parallelDBResult2                  // Parallel
      valid   <- getValidatedResult(first,second)   // implicit lift
      another <- 1230                               // implicit lift
      third   <- invokeSaga[Double,Int](12.45)      // Sequential but uses Future
      k       <- sub(second,first)                  // Sequential but uses Future
      p       <- add (k, third)                     // Sequential but uses Future
      m       <- ifF(valid){
        sub(p,another)
      }{p}   // Sequential but uses Future
    } yield m
  }*/

  def processAPIRequest(implicit arith:Arithmetic.Injects[Expr], dbo:DBOperation.Injects[Expr], sagaOp:Saga.Injects[Expr]): Free[Expr, Int] = {
    import arith._,dbo._,sagaOp._
    for {
      first  <- getFromDB("test1")
      second <- getFromDB("test4")
      third   <- invokeSaga[Double,Int](12.45)
      k       <- sub(second,first)                  // Sequential but uses Future
      p       <- add (k, third)                     // Sequential but uses Future
    } yield p
  }

  object APIModel {
    def execAPI()= {
      val inter = processAPIRequest.foldMap(interpreter)
      val run1 = Await.result(inter, 10 seconds)
      println(run1)
    }
  }

  APIModel.execAPI()
}
