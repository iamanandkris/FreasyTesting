package another

import actors.SagaActor
import akka.actor.ActorSystem
import akka.util.Timeout
import cats.{Monad, ~>}
import cats.data.Coproduct
import cats.free.{Free, Inject}

import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by anand on 31/03/17.
  */
object FreeFuture extends App{
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


  sealed trait ArithF[A]
  case class Add(x: Int, y: Int) extends ArithF[Int]
  case class Sub(x:Int, y:Int) extends ArithF[Int]
  class Arith[F[_]](implicit I: Inject[ArithF, F]) {
    def add(x: Int, y: Int) : Free[F,Int] = Free.inject[ArithF,F](Add(x,y))
    def sub(x: Int, y: Int) : Free[F,Int] = Free.inject[ArithF,F](Sub(x,y))
  }
  object Arith {
    implicit def arith[F[_]](implicit I: Inject[ArithF,F]): Arith[F] =new Arith[F]
  }

  sealed trait SagaAlgebra[A]
  case class Query[A,B](x:A) extends SagaAlgebra[B]
  class SagaOpFac[F[_]](implicit I: Inject[SagaAlgebra, F]) {
    def invokeSaga[A,B](x:A) : Free[F,B] = Free.inject[SagaAlgebra,F](Query[A,B](x))
  }
  object SagaOpFac {
    implicit def sagaQuery[F[_]](implicit I: Inject[SagaAlgebra,F]): SagaOpFac[F] =new SagaOpFac[F]
  }


  sealed trait DBOperation[A]
  case class Get(key:String) extends DBOperation[Int]
  case class Put(key:String, value:Int) extends DBOperation[Unit]
  class DBOpFac[F[_]](implicit I: Inject[DBOperation, F]) {
    def getFromDB(key:String) : Free[F,Int] = Free.inject[DBOperation,F](Get(key))
    def putToDB(key:String, value:Int) : Free[F,Unit] = Free.inject[DBOperation,F](Put(key,value))
  }
  object DBOpFac {
    implicit def dbo[F[_]](implicit I: Inject[DBOperation,F]): DBOpFac[F] =new DBOpFac[F]
  }


  type Expr1[A] = Coproduct[ArithF, DBOperation, A]
  type Expr[A] = Coproduct[SagaAlgebra, Expr1, A]
  type ResultF[A] = Future[A]

  object ArithIdF  extends (ArithF ~> ResultF) {
    def apply[A](fa: ArithF[A]) = fa match {
      case Add(x,y) => Future{x + y}
      case Sub(x,y) => Future{x - y}
    }
  }

  object DBOperationF  extends (DBOperation ~> ResultF) {
    val mapdata = mutable.Map.empty[String, Int]
    mapdata+=("test1" -> 100)
    mapdata+=("test4" -> 200)

    def apply[A](fa: DBOperation[A]) = fa match {
      case Get(x) => Future{mapdata.getOrElse(x, 0)}
      case Put(x,y) => Future{mapdata+(x -> y); ()}
    }
  }

  object SagaInter  extends (SagaAlgebra ~> ResultF) {
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout = Timeout(5 seconds)
    def apply[A](fa: SagaAlgebra[A]) = fa match {
      case Query(x) => {
        val actorSystem = ActorSystem("testActorsystem")
        val actor = actorSystem.actorOf(SagaActor.props)
        (actor ? x).map(x => x.asInstanceOf[A])
      }
    }
  }

  val interpreter1: Expr1 ~> ResultF = ArithIdF or DBOperationF
  val interpreter:Expr ~> ResultF = SagaInter or interpreter1

  def getValidatedResult(first:Int,second:Int):Boolean = first < second

  def liftToFree[T[?],B](processMessageResult:B):Free[T, B] =
    Free.pure[T, B] (processMessageResult)


  def ifF[T[?],B](condition : => Boolean)(left : => Free[T, B])(right : => B):Free[T, B] =
    if (condition) left else liftToFree(right)


  implicit def lift[T[?],B](value: => B): Free[T, B] ={
    Free.pure[T,B](value)
  }

  //implicit def lift1[B](value: => B): Free[Expr1, B] ={
  //  Free.pure[Expr1,B](value)
  //}


  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

  def processAPIRequest()(implicit arith : Arith[Expr], dbo:DBOpFac[Expr], sagaOp:SagaOpFac[Expr]): Free[Expr, Int] = {
    import arith._, dbo._,sagaOp._

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
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////


  object APIModel {
    def execAPI()= {
      val inter: Future[Int] = processAPIRequest.foldMap(interpreter)
      val run1 = Await.result(inter, 10 seconds)
      println(run1)
    }
  }

  APIModel.execAPI()
}
