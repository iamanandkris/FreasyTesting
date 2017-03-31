package initial.interpreters

import cats.~>
import initial.coproducts.coproducts.{Expr, Result}
import initial.dsls.dsl.{Arithmetic, IntValue}

/**
  * Created by anand on 27/03/17.
  */
object interpeters {
  object IntValueInterpreter extends IntValue.Interp[Result] {
    override def intValue(n: Int): Result[Int] = n
    override def intValueSquare(n: Int): Result[Int] = n * n
  }
  object ArithmeticInterpreter extends Arithmetic.Interp[Result] {
    def add(x: Int, y: Int): Result[Int] = x + y
  }

  val interpreter1: Expr ~> Result = ArithmeticInterpreter.interpreter or IntValueInterpreter.interpreter
}
