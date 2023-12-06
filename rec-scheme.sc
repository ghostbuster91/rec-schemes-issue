//> using lib "org.typelevel::cats-core::2.10.0"
//> using lib "io.higherkindness::droste-core::0.9.0"
import higherkindness.droste.data.Fix
import higherkindness.droste.GAlgebra
import higherkindness.droste.Algebra
import higherkindness.droste.scheme
import higherkindness.droste.Gather

import scala.annotation.tailrec

enum MyAst {
  case Number(value: BigInt)
  case OpPlus(left: MyAst, right: MyAst)
}
// 1 + 1 + 1 
def unfold(n: Int): MyAst =
  if (n > 0) MyAst.OpPlus(unfold(n - 1), MyAst.Number(n)) else MyAst.Number(n)

@tailrec
def unfoldRec(n: Int, acc: MyAst): MyAst =
  if (n > 0) unfoldRec(n - 1, MyAst.OpPlus(acc, MyAst.Number(n))) else acc

val amount = 1000000
// val aTree = unfold(amount)
val aTree = unfoldRec(amount, MyAst.Number(0))
// println(aTree)

def calculator(myAst: MyAst): BigInt = {
  myAst match
    case MyAst.Number(value) => value
    case MyAst.OpPlus(left, right) =>
      calculator(left) + calculator(right)
}

/* println(calculator(aTree)) */

import cats.Functor
import cats.Monad
import cats.Traverse
import cats.syntax.all._
import cats.~>

case class Fix2[F[_]](unfix: F[Fix2[F]])

// copied directly from smithy4s
object recursion {

  def hylo[F[_]: Functor, A, B](unfold: A => F[A], fold: F[B] => B)(a: A): B =
    fold(unfold(a).map(hylo(unfold, fold)))

  def hyloM[M[_]: Monad, F[_]: Traverse, A, B](
      unfold: A => M[F[A]],
      fold: F[B] => M[B]
  )(a: A): M[B] = {
    type MF[T] = M[F[T]] // composition of M and F
    implicit val MF: Functor[MF] = Functor[M].compose(Functor[F])
    val F = Traverse[F]
    def foldM(mfmb: M[F[M[B]]]): M[B] = for {
      fmb <- mfmb
      fb <- F.sequence[M, B](fmb)
      f <- fold(fb)
    } yield f

    hylo[MF, A, M[B]](unfold, foldM)(a)
  }

  def cata[F[_]: Functor, B](fold: F[B] => B)(tree: Fix2[F]): B =
    hylo[F, Fix2[F], B](_.unfix, fold)(tree)

  def ana[F[_]: Functor, A](unfold: A => F[A])(a: A): Fix2[F] =
    hylo[F, A, Fix2[F]](unfold, Fix2(_))(a)

  def anaM[M[_]: Monad, F[_]: Traverse, A](unfold: A => M[F[A]])(
      a: A
  ): M[Fix2[F]] =
    hyloM[M, F, A, Fix2[F]](unfold, x => Monad[M].pure(Fix2(x)))(a)

  def preprocess[F[_]: Functor](nt: F ~> F)(tree: Fix2[F]): Fix2[F] =
    cata[F, Fix2[F]](ff => Fix2(nt(ff)))(tree)

}

sealed trait MyAstF[A]
object MyAstF {

  final case class Number[A](result: BigInt) extends MyAstF[A]
  final case class OpPlus[A](left: A, right: A) extends MyAstF[A]

  implicit val functor: Functor[MyAstF] = new Functor[MyAstF] {
    override def map[A, B](sa: MyAstF[A])(f: A => B): MyAstF[B] =
      sa match {
        case Number(result)      => Number(result)
        case OpPlus(left, right) => OpPlus(f(left), f(right))
      }
  }
}

def fixedCalculator(myAst: MyAstF[BigInt]): BigInt = {
  myAst match
    case MyAstF.Number(value) => value
    case MyAstF.OpPlus(left, right) =>
      left + right
}

@tailrec
def unfoldRecForFix(n: Int, acc: Fix[MyAstF]): Fix[MyAstF] =
  if (n > 0) {
    val b = Fix[MyAstF](MyAstF.Number(n))
    unfoldRecForFix(
      n - 1,
      Fix(MyAstF.OpPlus[Fix[MyAstF]](acc, b))
    )
  } else acc

val aFixedTree: Fix[MyAstF] =
  unfoldRecForFix(amount, Fix[MyAstF](MyAstF.Number(0)))

// println(aFixedTree)
//
//
// val a = recursion.cata(fixedCalculator)(aFixedTree)
//
// println(a)


val fromNatAlgebra: Algebra[MyAstF,BigInt] = Algebra {
    case MyAstF.OpPlus(left, right) => left + right
    case MyAstF.Number(v)=> v
}

val result = scheme.cata(fromNatAlgebra).apply(aFixedTree)
println
