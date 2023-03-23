# ReplaceWith specification

* **Type**: TODO
* **Author**: TODO
* **Status**: TODO
* **Prototype**: TODO

- [ReplaceWith specification](#replacewith-specification)
  - [The problem](#the-problem)
  - [Proposed `replaceWith` specification](#proposed-replacewith-specification)
  - [Corner case: inlining is impossible](#corner-case-inlining-is-impossible)
- [Concrete syntax suggestions](#concrete-syntax-suggestions)
  - [Feature use-cases](#feature-use-cases)
- [Examples](#examples)
    - [Let ''out from nowhere'' source](#let-out-from-nowhere-source)
    - [Argument initialization](#argument-initialization)
    - [function with extension receiver](#function-with-extension-receiver)
- [Current bugs \& proposals](#current-bugs--proposals)

## The problem

The current `replaceWith` implementation has no specification, only a brief description in [API](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-replace-with/), a set of [tests](TODO: add links), and a several blog posts (TODO: add links).
Moreover, its behaviour seems to be quite unexpected in some cases.
Thus, it's reasonable to provide a simple feature specification and fix its implementation according to the specification.

## Proposed `replaceWith` specification

1. (Methods, functions, and constructors) Consider the replacement expression as a new body of the function/method/constructor/... to be replaced, then inline the call/usage.
2. (Replace one class (name) with another). Replace old class name with new class name. During transformation check for errors? ; NB, it may leeds to errors.

<!-- 
2. (replace one class with another) TODO [example](https://github.com/DaniilStepanov/bbfgradle/blob/f47406356a160ac61ab788f985b37121cc2e2a2a/tmp/arrays/youTrackTests/8727.kt#L3)
3. (replace one class/constructor with function/method) TODO [example](https://github.com/woocommerce/woocommerce-android/blob/c60a43f0b4c13c4d2f0d1d08a115bf3c42b157b0/WooCommerce/src/main/kotlin/com/woocommerce/android/tools/SelectedSite.kt#L89)
4. Expected IDE behaviour in case of error during transformation or when inlining is impossible: TODO -->

Pros:
1. *Simplicity*. The specification is quite simple, easy to describe, and, what is most important, easy to understand by users.
2. The replacement expression is a *usual valid Kotlin code*; thus it can be analysed as any other code and checked for errors.
3. It seems to be that at least most of the use cases should be already covered by inliner.

Cons:
1. It narrows down the problem of `replaceWith` implementation to inlining; **maybe** this is asking too much of the inliner.

## Corner case: inlining is impossible

$\bullet$ Sometimes inlining are impossible. For example:
``` Kotlin
fun testT2(x: X) {
    x.o̶l̶d̶F̶u̶n̶X̶()
}
class X {
    @Deprecated("", ReplaceWith("newFunX()"))
    fun oldFunX() {}
    private fun newFunX() {}
}
fun newFunX() {}
``` 

The expected behaviour is to replace `x.oldFunX()` with `x.newFunX()` since `newFunX()` corresponds to `this.newFunX(`) in the usual Kotlin code.
However, since method `newFunX` is private, inlining is impossible.

$\bullet$ Another example:
``` Kotlin
class Box<T>(private var i : T) {
    inline fun f() {
        set(get())
    }

    fun get(): T = i
    fun set(x: T) { i = x }
}

fun <T> Box<T>.c() = f()
fun aa() : Box<*> = Box<Int>(5)
fun test() {
    val x: Box<*> = aa()
    x.let {it.set(it.get() as Nothing)}
}
```

# Concrete syntax suggestions

1. It would be great to provide all usual checks inside an expression to be replaced with.
Ideally, something like
```Kotlin
@Deprecated(
    message = "deprecated",
    replaceWith = ReplaceWith(<imports>) {
        <new_body>
    }
)
fun foo (...) { <old_body> }
```
instead of
```Kotlin
@Deprecated(
    message = "deprecated",
    replaceWith = ReplaceWith(expression="<new_body>", <imports>)
)
fun foo (...) { <old_body> }
```

2. [KT-56969 Use body of deprecated function instead of ReplaceWith()](https://youtrack.jetbrains.com/issue/KT-56969/Use-body-of-deprecated-function-instead-of-ReplaceWith)
    Good point.
    It corresponds to the specification.
    But I'd prefer to use something like `$body$` here or `ReplaceWithBody()`, i.e. a successor of `ReplaceWith` .

3. In case of empty expression the call should be removed.
    TODO: add example.

## Feature use-cases

1. Update API and save old interface at least temporary.
2. Replace one class to another.
3. 
4. TODO (just describe a list of them without examples)


# Examples 

### Let ''out from nowhere'' [source](https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/idea/tests/testData/quickfix/deprecatedSymbolUsage/argumentSideEffects/complexExpressionNotUsedSafeCall.kt)

``` Kotlin
class C {
    @Deprecated("", ReplaceWith("newFun()"))
    fun oldFun() {
        newFun()
    }
}

fun newFun(){}

fun getC(): C? = null

fun foo() {
    // before
    getC()?.<caret>o̶l̶d̶F̶u̶n̶()
    //after
    getC()?.let { newFun() }
}
```

The behaviour is expected and corresponds to the specification.

### Argument initialization

Before
``` Kotlin
package ppp
fun bar(): Int = 0
@Deprecated("", ReplaceWith("newFun()"))
fun oldFun(p: Int = ppp.bar()) {
    newFun()
}
fun newFun(){}
fun foo() {
    <caret>o̶l̶d̶F̶u̶n̶()
}
```
After
``` Kotlin
package ppp
fun bar(): Int = 0
@Deprecated("", ReplaceWith("newFun()"))
fun oldFun(p: Int = ppp.bar()) {
    newFun()
}
fun newFun(){}
fun foo() {
    bar()
    newFun()
}
```

What do we expect? According to the proposed specification, this behaviour seems to be right: since we consider the expression as a new body of `oldFun`, inliner should call `bar` here (it may have side effects). 
In this particular case, there is no sense to call `bar` but then we expect the inliner to use some kind of static analysis to decide this.
AFAIK, the inliner is not able to handle such optimization (and it is too computationally expensive).

### function with extension receiver

``` Kotlin
fun test() {
    KotlinAPI().f̶o̶o̶() // -> KotlinApi().bar()
}

class KotlinAPI {
    fun bar() {}
}

@Deprecated(
    message = "deprecated",
    replaceWith = ReplaceWith(expression = "this.bar()") // looks like ‘this’ is redundant here, tested
)
fun KotlinAPI.foo() {}
```

According to the proposed specification both `this.bar()` and `bar()` can be used here.

# Current bugs \& proposals

$\bullet$ Incorrect expression

In case of an incorrect expression or any other fail `replaceWith` just removes the expression to be replaced.

Simplest example:

``` Kotlin
@Deprecated(message = "deprecated", replaceWith = ReplaceWith(expression = "b1"))
fun f1() { 1 }
//before
fun testF1 () { f̶1̶() }
// after
fun testF1 () {}
```

Expected behaviour could be to show the error in `replaceWith` expression and either perform no replacements while the expression is not error-free or provide some kind of error during the replacement.

$\bullet$ [KTIJ-13679 @Deprecated ReplaceWith method does not convert to a property](https://youtrack.jetbrains.com/issue/KTIJ-13679/Deprecated-ReplaceWith-method-does-not-convert-to-a-property)

``` Kotlin
@Deprecated("Nice description here", replaceWith = ReplaceWith("isVisible = visible"))
fun C.something(visible: Boolean)  { 
    // Something useful here 
}

class C(){ var isVisible: Boolean = false }

fun use(){ C().s̶o̶m̶e̶t̶h̶i̶n̶g̶(false) }

// Expected:
C().isVisible = false
// Reality
C()
```

The proposed specification covers this case.

$\bullet$ [KTIJ-16495 Support `ReplaceWith` for constructor delegation call](https://youtrack.jetbrains.com/issue/KTIJ-16495/Support-ReplaceWith-for-constructor-delegation-call)

``` Kotlin
open class A(val s: String, val i: () -> Int, val i2: Int) {
    @Deprecated("Replace with primary constructor", ReplaceWith("C(s = \"\", a = { i }, m = i)"))
    constructor(i: Int) : this("", { i }, i)
}

class T: A {
    constructor(): s̶u̶p̶e̶r̶(33)
    constructor(i: Int): s̶u̶p̶e̶r̶(i)
}
```

Currently is not supported but perfectly fits to the proposed specification.

$\bullet$ [KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors](https://youtrack.jetbrains.com/issue/KTIJ-6112/ReplaceWith-quickfix-is-not-suggested-for-property-accessors)

Doesn't work now but the expected behaviour corresponds to the proposed specification.

$\bullet$ [KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors](https://youtrack.jetbrains.com/issue/KTIJ-6112/ReplaceWith-quickfix-is-not-suggested-for-property-accessors)


$\bullet$ typealiases

``` Kotlin
//======================================================================================================================
@Deprecated("", ReplaceWith("NewClass"))
class OldClass @Deprecated("", ReplaceWith("NewClass(12)")) constructor()

class NewClass(p: Int = 0)

typealias Old = OldClass // -> NewClass // doesn't work: BUG: it tries to replace constructor instead of class name

val a = Old() // -> NewClass(12) // ok, works
//======================================================================================================================
```

$\bullet$ [KTIJ-11679 ReplaceWith replacing method call to property set ends up removing the line of code](https://youtrack.jetbrains.com/issue/KTIJ-11679/ReplaceWith-replacing-method-call-to-property-set-ends-up-removing-the-line-of-code)

``` Kotlin
@Deprecated("", ReplaceWith("new = value"))
fun Int.old(value: Int) = Unit
var Int.new: Int
    get() = 0
    set(value) = Unit

fun aFunction() {
    1.old(0) // Quick-fix me
}
```