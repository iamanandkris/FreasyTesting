package initial.dsls

import cats.free.Free
import freasymonad.cats.free

/**
  * Created by anand on 27/03/17.
  */
object dsl {
  @free trait IntValue {
    type IntVal[A] = Free[Adt, A]
    sealed trait Adt[A]
    def intValue(n: Int): IntVal[Int]
    def intValueSquare(n: Int): IntVal[Int]
  }
  @free trait Arithmetic {
    type Arith[A] = Free[Adt, A]
    sealed trait Adt[A]
    def add(x: Int, y: Int): Arith[Int]
  }
}
