package initial
import initial.programs.programs._
import initial.interpreters.interpeters._

object FreasyFree  extends App {
  val run1 = expr1.foldMap(interpreter1)
  println(run1)
}
