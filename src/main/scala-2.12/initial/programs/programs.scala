package initial.programs

import cats.free.Free
import initial.coproducts.coproducts.Expr
import initial.dsls.dsl.{Arithmetic, IntValue}

/**
  * Created by anand on 27/03/17.
  */
object programs {
  def expr1(implicit value: IntValue.Injects[Expr], arith: Arithmetic.Injects[Expr]): Free[Expr, Int] = {
    import value._, arith._
    for {
      n <- intValueSquare(2)
      m <- add(n, n)
    } yield m
  }
}
