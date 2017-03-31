package initial.coproducts

import cats.Id
import cats.data.Coproduct
import initial.dsls.dsl.{Arithmetic, IntValue}

/**
  * Created by anand on 27/03/17.
  */
object coproducts {
  type Expr[A] = Coproduct[Arithmetic.Adt, IntValue.Adt, A]
  type Result[A] = Id[A]
}
