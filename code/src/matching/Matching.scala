package matching

import core.Algebras.AlgebraDefault

object Matching {

  trait ExpAlg[In, Out] {
    def Lit(x : Int) : Out
    def Add(e1 : In, e2 : In) : Out
  }

  trait InvExp[Exp] {
    val fromLit : Option[Int]
    val fromAdd : Option[(Exp, Exp)]
  }

  trait InvExpAlg[In] extends ExpAlg[In, InvExp[In]] {
    def Lit(x : Int) = new InvExp[In] {
      val fromLit = Some(x)
      val fromAdd = None
    }
    def Add(e1 : In, e2 : In) = new InvExp[In] {
      val fromLit = None
      val fromAdd = Some(e1, e2)
    }
  }

  object invExpAlg extends InvExpAlg[EvalInvExp]

  def invExpAlg2[In] : ExpAlg[In, InvExp[In]] = new InvExpAlg[In] {}

  trait Eval {
    def eval : Int
  }

  trait Print {
    def print : String
  }

  /*
  trait Eval[In] {
    def eval : Int
  }*/

  trait PatternExpAlg[In <: InvExp[In], Out] extends ExpAlg[In, Out] { //Invertible observations for any constructor! 
    // Adding observations 
    //def fromLit(e : In) : Option[Int] = e.fromLit 
    //def fromAdd(e : In) : Option[(In,In)] = e.fromAdd 

    // Adding Observations with sugar! 
    object Lit { def unapply(e : In) : Option[Int] = e.fromLit }
    object Add { def unapply(e : In) : Option[(In, In)] = e.fromAdd }
  }

  trait EvalExpAlg[In <: InvExp[In] with Eval] extends PatternExpAlg[In, Eval] {
    def Lit(x : Int) = new Eval { def eval = x }

    def Add(e1 : In, e2 : In) = new Eval {
      //      def eval = e1.fromLit match { 
      //        case Some(n) => System.out.println("Been here"); n + e2.eval 
      //        case None => e1.eval + e2.eval 
      //      }  
      def eval = e1 match {
        case Lit(n)    =>
          System.out.println("Been here"); n + e2.eval
        case Add(_, _) =>
          System.out.println("Been there"); e1.eval + e2.eval
        case _         => e1.eval + e2.eval
      }
    }
  }

  object evalExpAlg extends EvalExpAlg[EvalInvExp]

  def evalExpAlg2[In <: InvExp[In] with Eval] : ExpAlg[In, Eval] = new EvalExpAlg[In] {}

  // Structural equality with Object Algebras 
  trait Eq[In] {
    def equal(o : In) : Boolean
  }

  trait EqExpAlg[In <: InvExp[In] with Eq[In]] extends PatternExpAlg[In, Eq[In]] {
    def Lit(x : Int) = new Eq[In] {
      def equal(o : In) = o match {
        case Lit(y) => x == y // calls fromLit 
        case _      => false
      }
    }

    def Add(e1 : In, e2 : In) = new Eq[In] {
      def equal(o : In) = o match {
        case Add(e3, e4) => e1.equal(e3) && e2.equal(e4) // calls fromAdd 
        case _           => false
      }
    }
  }

  trait EvalInvExp extends Eval with InvExp[EvalInvExp] // type-level Fixpoint: seems necessary for allowing binary method-like stuff. No real need for F-bounds 

  def mix(a : Eval, b : InvExp[EvalInvExp]) : EvalInvExp = new EvalInvExp {
    val fromLit = b.fromLit
    val fromAdd = b.fromAdd
    def eval = a.eval
  }

  // Mixing the 2 algebras 
  trait EvalWithInAlg extends ExpAlg[EvalInvExp, EvalInvExp] {
    def Lit(x : Int) = mix(evalExpAlg.Lit(x), invExpAlg.Lit(x))
    def Add(e1 : EvalInvExp, e2 : EvalInvExp) = mix(evalExpAlg.Add(e1,
      e2), invExpAlg.Add(e1, e2))
  }

  trait ExpMerge[S, A, B] extends ExpAlg[S, A with B] {
    val lift : A => B => A with B
    val alg1 : ExpAlg[S, A]
    val alg2 : ExpAlg[S, B]

    def Lit(x : Int) : A with B =
      lift(alg1.Lit(x))(alg2.Lit(x))

    def Add(e1 : S, e2 : S) : A with B =
      lift(alg1.Add(e1, e2))(alg2.Add(e1, e2))
  }

  def merge[S, A, B](mix : A => B => A with B, a1 : ExpAlg[S, A], a2 : ExpAlg[S, B]) : ExpAlg[S, A with B] = new ExpMerge[S, A, B] {
    val lift = mix
    val alg1 = a1
    val alg2 = a2
  }

  def mix2[In] : Eval => InvExp[In] => Eval with InvExp[In] = a => b => new Eval with InvExp[In] {
    val fromLit = b.fromLit
    val fromAdd = b.fromAdd
    def eval = a.eval
  }

  // crazy coercions
  def fun2(o : Eval with InvExp[EvalInvExp]) : EvalInvExp = new EvalInvExp() {
    val fromLit = o.fromLit
    val fromAdd = o.fromAdd
    def eval = o.eval
  }

  def fun(alg : ExpAlg[EvalInvExp, Eval with InvExp[EvalInvExp]]) : ExpAlg[EvalInvExp, EvalInvExp] =
    new ExpAlg[EvalInvExp, EvalInvExp] {
      def Lit(x : Int) : EvalInvExp = fun2(alg.Lit(x))
      def Add(e1 : EvalInvExp, e2 : EvalInvExp) : EvalInvExp = fun2(alg.Add(e1, e2))
    }

  def dual(alg : ExpAlg[EvalInvExp, Eval with InvExp[EvalInvExp]]) : ExpAlg[Eval with InvExp[EvalInvExp], Eval with InvExp[EvalInvExp]] =
    new ExpAlg[Eval with InvExp[EvalInvExp], Eval with InvExp[EvalInvExp]] {
      def Lit(x : Int) : Eval with InvExp[EvalInvExp] = alg.Lit(x)
      def Add(e1 : Eval with InvExp[EvalInvExp], e2 : Eval with InvExp[EvalInvExp]) : Eval with InvExp[EvalInvExp] = alg.Add(fun2(e1), fun2(e2))
    }

  def dual2(alg : ExpAlg[EvalInvExp, Eval with InvExp[EvalInvExp]]) : ExpAlg[Eval with InvExp[EvalInvExp], Eval with InvExp[EvalInvExp]] =
    alg.asInstanceOf[ExpAlg[Eval with InvExp[EvalInvExp], Eval with InvExp[EvalInvExp]]]
  
  
  // To discuss
  def closeS[S <: A with B, A, B](alg : ExpAlg[S, A with B]) : ExpAlg[A with B, A with B] = alg.asInstanceOf[ExpAlg[A with B, A with B]]

  def test5 = {
    
    def pre[In <: InvExp[In] with Eval] : ExpAlg[In, InvExp[In] with Eval]= merge[In,Eval,InvExp[In]](mix2, evalExpAlg2, invExpAlg2)
    
    def pre2 : ExpAlg[EvalInvExp, Eval with InvExp[EvalInvExp]] = pre[EvalInvExp]  // In = EvalInvExp
    
    def o = exp(closeS(pre2))
    println("Eval:" + o.eval + "\nfromLit:" + o.fromLit + "\nfromAdd:" + o.fromAdd)
    
    /*
    def o2 = exp(fun(pre2))
    println("Eval:" + o2.eval + "\nfromLit:" + o2.fromLit + "\nfromAdd:" + o2.fromAdd)
    * 
    */
    

  }

  //trait Merge[F[_],G[_],In] extends ExpAlg[EvalInvExp, EvalInvExp] {
  //  def Lit(x : Int) = mix(evalExpAlg.Lit(x), invExpAlg.Lit(x))
  //  def Add(e1 : EvalInvExp, e2 : EvalInvExp) = mix(evalExpAlg.Add(e1,
  //    e2), invExpAlg.Add(e1, e2))
  //}

  object EvalWithInAlg extends EvalWithInAlg

  def exp[E](alg : ExpAlg[E, E]) = {import alg._; Add(Lit(3), Lit(4))}

  def test = System.out.println(exp(EvalWithInAlg).eval) // combine(evalAlg,invAlg) 

}
//  object Test {
//    import Exp._
//    object ExpComb extends Algebra[ExpAlg]
//  
//    def test = {
//      import ExpComb._
//      val o = exp(merge[IEval, IPrint](LiftEP,ExpEval,ExpPrint))
//      println("Eval: " + o.eval() + "\nPrint:" + o.print())
//    }
//  }
object Test {
  import Matching._
  object ExpComb extends AlgebraDefault[ExpAlg]

  def test = {
    import ExpComb._
    
  }
}

object Main {
  import Matching._
  def main(args : Array[String]) {
    test5
  }
}