import javax.naming.SizeLimitExceededException

//=========================================================================
///=========================================================================
// GADT
//=========================================================================
//=========================================================================
// sealed class Expr<A>(val a : A)
//class NumberExpr(val i: Int): Expr<Int>(i)
////class StrExpr(val s: String): Expr<String>(s)
//
//fun <T>eval(e: Expr<T>) : T =
//    when (e) {
//        is NumberExpr -> e.i
//        //else       -> e.a
//    }
//fun aaa() {
//    val a : Int = eval(NumberExpr(1))
//}
//
//interface CTag<A>
//open class T1
//open class T1Tag: CTag<T1>
//class T2: T1()
//class T2Tag: T1Tag(), CTag<T2>

//=========================================================================
//=========================================================================
// ReplaceWith
//=========================================================================
//=========================================================================
class A {
    @Deprecated("",
        ReplaceWith("bbb()",
            "net.plt.lab.bbb"))
    fun aaa() {
        bbb()
    }
}

fun bbb() {
}


fun testt() {
    foo()
    5.faz(1, { g -> print(g) })
    val t = 6
    ({ 5 + 5 }.invoke()).faz(1) { g -> print(g) }
    print(t)


}

//======================================================================================================================
// Some bullshit:
//    Cannot infer type of parameter in replacement expression
// myMap { println("hello") }
// -->
// { println("hello") }
// newMap({ _ -> () })
//======================================================================================================================
@Deprecated(message = "something", replaceWith = ReplaceWith(expression = "newMap({ _ -> () })"))
fun myMap(f: () -> Unit) {}


fun newMap(g: () -> Unit) {}

fun tests( ){
    myMap { println("hello") }
}
//======================================================================================================================
// function with extension receiver
//======================================================================================================================
class KotlinAPIT {
    fun barT() {}
}

@Deprecated(
    message = "deprecated",
    replaceWith = ReplaceWith(expression = "this.barT()") // both `barT()` or `this.barT()` could be used here
)
fun KotlinAPIT.fooT() {}

fun testT() {
    KotlinAPIT().fooT() // -> KotlinApiT().bar()
}
//======================================================================================================================
// replace with undeclared function leads to error
// foo1 () ---> bar1 () // Undefined reference to bar1
//======================================================================================================================
@Deprecated(
    message = "deprecated",
    replaceWith = ReplaceWith(expression = "bar1()"),
//    level = DeprecationLevel.HIDDEN
)
fun foo1() {
    val l = listOf(1, 2, 3)
    l.forEach { print(it) }
}

fun testFoo1 () {
    foo1()
}
//======================================================================================================================
// incorrect expression leads to empty replacement; no matter if b1 is defined
//======================================================================================================================
@Deprecated(message = "deprecated", replaceWith = ReplaceWith(expression = "b1"))
fun f1() { 1 }
// fun b1(){}
fun testF1 () { f1() }
//======================================================================================================================
class Cc {
    @Deprecated("", ReplaceWith("newFun()"))
    fun oldFun() {
        newFun()
    }
}
fun getC(): Cc? = null
fun newFun(){}

fun foo() {
    getC()?.oldFun() // <caret>oldFun()
}

//======================================================================================================================
// drop receiver (ambiguity)
//======================================================================================================================
fun testT2(x: X) {
    x.oldFunX() // -> x.newFunX()
    // this is expected since newFunX() corresponds to X.newFunX in oldFunX body
    // Note, inliner should take into account that newFunY could be privite, then no inline is possible!!!
}
class X {
    @Deprecated("", ReplaceWith("newFunX()"))
    fun oldFunX() {}
    //private
    fun newFunX() {}
}
fun newFunX() {}
// proof
fun newFunY() {}
class Y {
    @Deprecated("", ReplaceWith("newFunY()"))
    fun oldFunY() { newFunY() }
    private fun newFunY() {}
}

//======================================================================================================================
// Support `ReplaceWith` for supertypes call (fixed; works)
// https://youtrack.jetbrains.com/issue/KTIJ-16462/Support-ReplaceWith-for-supertypes-call
//======================================================================================================================
abstract class AA(val i: () -> Int) {
    @Deprecated("Replace with primary constructor", ReplaceWith("AA({ i })"))
    constructor(i: Int) : this({ i })
}

// Expect result:
// class B : A({ 42 })
class BB : AA(42)
//======================================================================================================================
// @Deprecated ReplaceWith method does not convert to a property
// https://youtrack.jetbrains.com/issue/KTIJ-13679/Deprecated-ReplaceWith-method-does-not-convert-to-a-property
//======================================================================================================================
class CCC(){ var isVisible: Boolean = false }

@Deprecated("Nice description here", replaceWith = ReplaceWith("isVisible = visible"))
fun CCC.something(visible: Boolean)  {
    // Something useful here
}

fun useCCC(){
    CCC().something(false)
}

// Expected:
// CCC().isVisible = false
// Reality:
// CCC()
//======================================================================================================================
// KTIJ-8601 ReplaceWith: provide mechanism for removing the function call
// https://youtrack.jetbrains.com/issue/KTIJ-8601/ReplaceWith-provide-mechanism-for-removing-the-function-call
// Looks ok. I didn't get the point of issue since we can use `this` but maybe we should treat empty expression as
// a note to remove the call completely
//======================================================================================================================
fun <T> Collection<T>.unique(): Set<T> = toSet()
@Deprecated("Useless",
    replaceWith = ReplaceWith("this")
)
fun <T> Set<T>.unique() = this
fun fffff () {
    setOf(1).unique().joinToString()
}
//======================================================================================================================
// KTIJ-16495 Support `ReplaceWith` for constructor delegation call
// https://youtrack.jetbrains.com/issue/KTIJ-16495/Support-ReplaceWith-for-constructor-delegation-call
// Currently is not supported but should be
//======================================================================================================================
open class E(val s: String, val i: () -> Int, val i2: Int) {
    @Deprecated("Replace with primary constructor", ReplaceWith("C(\"str\", { i }, i)"))
    constructor(i: Int) : this("", { i }, i)
}

class T: E {
    constructor(): super(33)
    constructor(i: Int): super(i)
}
//======================================================================================================================
// KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors
// https://youtrack.jetbrains.com/issue/KTIJ-6112/ReplaceWith-quickfix-is-not-suggested-for-property-accessors
// Doesn't work now but is ok for inline
// ======================================================================================================================
var deprecated: Int = 42
    @Deprecated("", ReplaceWith("other"))
    get
    @Deprecated("", ReplaceWith("other = value"))
    set

var other = 33

fun testR() {
    println(deprecated) // -expected-> println(other)
    deprecated = 33 // -expected-> other = 33
}

// and

fun ffff(c: Q) {
    c.property
    c.property = c.property
}
class Q {
    var property: String
        @Deprecated(
            "Use getter accessor method instead.",
            level = DeprecationLevel.WARNING,
            replaceWith = ReplaceWith("function()")
        )
        get() = TODO()
        @Deprecated(
            "Use setter accessor method instead.",
            level = DeprecationLevel.WARNING,
            replaceWith = ReplaceWith("function(value)")
        )
        set(value) {
            TODO()
        }

    fun function() : String = TODO()
    fun function(name: String): Unit = TODO()
}
//======================================================================================================================
//typealias
//======================================================================================================================
@Deprecated("Event bus is considered deprecated.", ReplaceWith("BBB"))
class AAA constructor (val i: Int)

typealias Old3 = AAA // -> BBB // works

fun testAAA () {
    val e = AAA (5) // -> BBB (5) // may leeds to error
}
//======================================================================================================================
fun observe () { }
@Deprecated("Event bus is considered deprecated.", ReplaceWith("observe()"))
class AAAA(val i: Int)

typealias Old2 = AAAA // replacement is impossible --- ok

fun tetstAAAA () {
    val e = AAAA (5) // -> observe () --- ok, works
}
//======================================================================================================================
@Deprecated("", ReplaceWith("NewClass"))
class OldClass @Deprecated("", ReplaceWith("NewClass(12)")) constructor()

class NewClass(p: Int = 0)

typealias Old = OldClass // -> NewClass // doesn't work: BUG: it tries to replace constructor instead of class name

val a = Old() // -> NewClass(12) // ok, works
//======================================================================================================================
//======================================================================================================================
//======================================================================================================================
@Deprecated(
    message = "deprecated",
//    replaceWith = ReplaceWith(expression = "baz(v, f)"),
    replaceWith = ReplaceWith(expression = "faz.ibaz(0, { _ -> })"),
//    replaceWith = ReplaceWith(expression = "faz.ibaz ?. ibaz"),
//    replaceWith = ReplaceWith(expression = "if (true) { faz.ibaz(this, ibaz) } else { println(false) }"),
)
fun Int.faz(faz : Int, ibaz : (Int) -> Unit) {
    faz.ibaz(this + this, ibaz)
}

//fun bar() {}
fun baz(v : Int, f : (Int) -> Unit) {
    f(6)
}
fun Int.ibaz(v : Int, f : (Int) -> Unit) {
    f(this + v)
}

fun test() {
    KotlinAPI().foo() // -> KotlinApi().bar()
}

class KotlinAPI {
    fun bar() {}
}

@Deprecated(
    message = "deprecated",
    replaceWith = ReplaceWith(expression = "bar()")
)
fun KotlinAPI.foo() {}

@Deprecated("Nice description here", replaceWith = ReplaceWith("isVisible = visible"))
fun D.something(visible: Boolean)  {
    isVisible = visible;
    isVisible = visible
    // Something useful here
}

class D(){
    var isVisible: Boolean = false
}

fun use(){
    D().something(false)
}

class C {
    var property: String
        @Deprecated(
            "Use getter accessor method instead.",
            level = DeprecationLevel.WARNING,
            replaceWith = ReplaceWith("function()")
        )
        get() = function()
        @Deprecated(
            "Use setter accessor method instead.",
            level = DeprecationLevel.WARNING,
            replaceWith = ReplaceWith("function(value)")
        )
        set(value) {
            function(value)
        }

    fun function() : String = TODO()
    fun function(name: String): Unit = TODO()
}
fun f(c: C) {
    c.property
    c.property = c.property
}

//fun main(args: Array<String>) {
//    println("Hello World!")
//
//    // Try adding program arguments via Run/Debug configuration.
//    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
//    println("Program arguments: ${args.joinToString()}")
//}