# `ReplaceWith` inspection specification

* **Type**: TODO
* **Author**: TODO
* **Status**: TODO
* **Prototype**: TODO

- [`ReplaceWith` inspection specification](#replacewith-inspection-specification)
  - [The problem](#the-problem)
  - [Feature use-cases](#feature-use-cases)
  - [Proposed `replaceWith` inspection specification](#proposed-replacewith-inspection-specification)
    - [Pros:](#pros)
    - [Cons:](#cons)
  - [Corner case: inlining is impossible](#corner-case-inlining-is-impossible)
  - [Corner case: ''separate/conflicting'' class name and methods replacements](#corner-case-separateconflicting-class-name-and-methods-replacements)
- [Concrete syntax suggestions](#concrete-syntax-suggestions)
- [Examples](#examples)
    - [Let ''out from nowhere'' source](#let-out-from-nowhere-source)
    - [Argument initialization](#argument-initialization)
    - [Function with extension receiver](#function-with-extension-receiver)
- [Current bugs \& proposals](#current-bugs--proposals)
  - [$\\bullet$ Incorrect expression](#bullet-incorrect-expression)
  - [$\\bullet$ KTIJ-13679 @Deprecated ReplaceWith method does not convert to a property](#bullet-ktij-13679-deprecated-replacewith-method-does-not-convert-to-a-property)
  - [$\\bullet$ KTIJ-16495 Support `ReplaceWith` for constructor delegation call](#bullet-ktij-16495-support-replacewith-for-constructor-delegation-call)
  - [$\\bullet$ KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors](#bullet-ktij-6112-replacewith-quickfix-is-not-suggested-for-property-accessors)
  - [$\\bullet$ KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors](#bullet-ktij-6112-replacewith-quickfix-is-not-suggested-for-property-accessors-1)
  - [$\\bullet$ typealiases](#bullet-typealiases)
  - [$\\bullet$ KTIJ-11679 ReplaceWith replacing method call to property set ends up removing the line of code](#bullet-ktij-11679-replacewith-replacing-method-call-to-property-set-ends-up-removing-the-line-of-code)
  - [$\\bullet$ KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors](#bullet-ktij-6112-replacewith-quickfix-is-not-suggested-for-property-accessors-2)
- [Discussion](#discussion)

## The problem

The current `replaceWith` implementation has no specification, only a brief description in [API](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-replace-with/), a set of [tests]([id-com-tests](https://github.com/JetBrains/intellij-community/tree/master/plugins/kotlin/idea/tests/testData/quickfix/deprecatedSymbolUsage)), and several blog posts ([1](https://dev.to/mreichelt/the-hidden-kotlin-gem-you-didn-t-think-you-ll-love-deprecations-with-replacewith-3blo),
[2](https://www.baeldung.com/kotlin/deprecation),
[3](https://todd.ginsberg.com/post/kotlin/deprecation/),
[4](https://readyset.build/kotlin-deprecation-goodies-a35a397aa9b5)) from users.
Moreover, its behaviour seems to be quite unexpected in some cases.
Thus, it's reasonable to provide a simple feature specification and fix its implementation according to the specification.

## Feature use-cases

1. Update API and save the old interface at least temporarily.
2. Replace one class (name) with another.
3. Provide an IDE-guided way to learn a library's API by providing a deprecated API, which looks like something well-known by users, i.e., like it is done with the (well-known) Flow library in
   [kotlinx.couritines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/flow/Migration.kt).

## Proposed `replaceWith` inspection specification

1. *Behaviour for functions, methods, and constructors (FMC).* <br />
    Consider the replacement expression as a new body of the function/method/constructor (FMC), then inline on a call site.
    + In case the FMC replacement expression is just a name, i.e., `A::f`, treat it as a shortcut for a call `A::f(<args>)` where `args` are the same as in FMC call.
2. *Behaviour for classes.* <br />
    First, it is assumed that in case of classes replacing one class name with another existing class only is possible.
    The expected behaviour is just to replace old class name with new class name keeping the context (class paths).
    Constructors are assumed to be also ReplacedWith new class constructors by replacing the class name only.
    NB, this case is error prone.
3. *Property/variable/field replacement.* <br />
   1. *Replace with another property/var/field.* <br />
    Straightforward replacement (Of cause it is assumed that the inliner is able to distinguish `get` and `set`).
   2. *`get` and/or `set` replacement.* <br />
    Straightforward, same as #1.
    Also, see [current bug](#bullet-ktij-6112-replacewith-quickfix-is-not-suggested-for-property-accessors-2)

### Pros:
1. *Simplicity*. The specification is quite simple, easy to describe, and, what is most important, easy to understand by users.
2. The replacement expression is a *usual valid Kotlin code*; thus it can be analysed as any other code and checked for errors.
3. It seems to be that most of the use-cases should be already covered by the inliner.

### Cons:
1. It narrows down the problem of `replaceWith` implementation to inlining; **maybe** this is asking too much of the inliner.
2. The proposed `replaceWith` specification is indeed quite simple.
But it requires user to understand how inliner works.
This observation rises an interesting question:
``**Does the inliner have a normal specification**?''


## Corner case: inlining is impossible

In case the inlining is impossible it is suggested to cancel the inspection with a corresponding error.
Analysis of ReplaceWith possibility, i.e. that inlining is possible in any usage scenario seems to be an amazing feature but is assumed to be too complex and expansive in the current proposal.

$\bullet$ Sometimes inlining is impossible. For example:
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

$\bullet$ Another example (no connection with `replaceWith`) of code when inlining is impossible:
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
    x.let {it.set(it.get() as Nothing)} // inlining is impossible
}
```

## Corner case: ''separate/conflicting'' class name and methods replacements

Simplest case
``` Kotlin
@Deprecated("", replaceWith = ReplaceWith("B"))
class A () {
    @Deprecated("", ReplaceWith("newFun()"))
    fun oldFun() {}
}

var a = A̶().o̶l̶d̶F̶u̶n̶()
```

In the current proposal it is assumed that these two deprecations are two separate inspections.
Thus, no conflict here, it is controlled by user which scenario to choose.
Scenarios:
1. First replace `oldFun` with `newFun`, then replace class name getting as the result `B.newFun()`
2. Replace class name `A` with `B` getting as the result `A.oldFun()`. The case is error prone as well as class name replacement in general.

# Concrete syntax suggestions

1. ReplaceWith expression analysis. It would be be great to provide syntax highlighting and code analysis (all usual inspections and errors) for ReplaceWith expression as for any other code.
2. I suggest (if it is possible) to redesign the concrete syntax by introducing explicit scope instead of expression. I.e. I'd prefer something like
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
First, it's more clear than current syntax. Second, I assume if it is possible to make a scope a part of AST (PSI) then the previous point will be supported ``for free''.

3. [KT-56969 Use body of deprecated function instead of ReplaceWith()](https://youtrack.jetbrains.com/issue/KT-56969/Use-body-of-deprecated-function-instead-of-ReplaceWith) <br/>
    The issue contains a special case of the ReplaceWith usage when deprecated methods body and ReplaceWith expressions are identical.
    It looks like just forcing the inlining.
    I guess it can be supported by either introducing `$body$` to refer to the existing body or `ReplaceWithBody()`, i.e. a successor of `ReplaceWith`.
4. In case of an empty ReplaceWith expression (or new code scope is empty, or something like `ReplaceWithRemove()` depending on concrete syntax), the call should be removed.
    Connected example: [KTIJ-8601 ReplaceWith: provide mechanism for removing the function call](https://youtrack.jetbrains.com/issue/KTIJ-8601/ReplaceWith-provide-mechanism-for-removing-the-function-call)
    ``` Kotlin
    fun <T> Collection<T>.unique(): Set<T> = toSet()
    @Deprecated("Useless",
        replaceWith = ReplaceWith("this") // or ReplaceWith(""), or ReplaceWithRemove(), or ReplaceWith() { }
    )
    fun <T> Set<T>.unique() = this
    fun test () {
        setOf(1).u̶n̶i̶q̶u̶e̶().joinToString() // -> setOf(1).joinToString()
    }
    ```

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

According to the proposed specification, this behaviour is right: since we consider the expression as a new body of `oldFun`, inliner should call `bar` here (it may have side effects).
In this particular case, there is no sense to call `bar` but then we expect the inliner to use some kind of static analysis to decide this.
AFAIK, the inliner is not able to handle such optimization (and it is too computationally expensive).

### Function with extension receiver

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

## $\bullet$ Incorrect expression

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

## $\bullet$ [KTIJ-13679 @Deprecated ReplaceWith method does not convert to a property](https://youtrack.jetbrains.com/issue/KTIJ-13679/Deprecated-ReplaceWith-method-does-not-convert-to-a-property)

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

## $\bullet$ [KTIJ-16495 Support `ReplaceWith` for constructor delegation call](https://youtrack.jetbrains.com/issue/KTIJ-16495/Support-ReplaceWith-for-constructor-delegation-call)

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

Currently is not supported but perfectly fits the proposed specification.

## $\bullet$ [KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors](https://youtrack.jetbrains.com/issue/KTIJ-6112/ReplaceWith-quickfix-is-not-suggested-for-property-accessors)

Doesn't work now but the expected behaviour corresponds to the proposed specification.

## $\bullet$ [KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors](https://youtrack.jetbrains.com/issue/KTIJ-6112/ReplaceWith-quickfix-is-not-suggested-for-property-accessors)


## $\bullet$ typealiases

``` Kotlin
//======================================================================================================================
@Deprecated("", ReplaceWith("NewClass"))
class OldClass @Deprecated("", ReplaceWith("NewClass(12)")) constructor()

class NewClass(p: Int = 0)

typealias Old = OldClass // -> NewClass // doesn't work: BUG: it tries to replace with constructor instead of class name

val a = Old() // -> NewClass(12) // ok, works
//======================================================================================================================
```

## $\bullet$ [KTIJ-11679 ReplaceWith replacing method call to property set ends up removing the line of code](https://youtrack.jetbrains.com/issue/KTIJ-11679/ReplaceWith-replacing-method-call-to-property-set-ends-up-removing-the-line-of-code)

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
Doesn't work now.
But it should, and it corresponds to the specification.

## $\bullet$ [KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors](https://youtrack.jetbrains.com/issue/KTIJ-6112/ReplaceWith-quickfix-is-not-suggested-for-property-accessors)

``` Kotlin
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
    c.property // -> c.function ()
    c.property = c.property // -> c.function (c.function())
}
```

Doesn't work now.
But it should, and it corresponds to the specification.

# Discussion

1. Should ReplaceWith replace constructors when replacing class name?
2. Even more, the question is **When should we suggest class name replacement?** if it is specified with `replaceWith`. <br/>
    In my opinion, class name replacement is a distinct case but users use it, so we have to specify the behaviour clearly and its connection with ``possibly conflicting'' methods replacements or even lead to errors.
3. What is the expected IDE behaviour when ReplaceWith leeds to an error (in the resulting code)?
    What if replace all gets errors in some cases?
4. Сan we provide an analysis that checks the correctness of a potential substitution? For example, show an error at the location of concrete ReplaceWith definition if any its usage would result in an error.
5. Connection between different deprecations.
    Consider, for example, again a [corner case: ''separate/conflicting'' class name and methods replacements](#corner-case-separateconflicting-class-name-and-methods-replacements) or [feature use-case \#3](#feature-use-cases).
    Could it be the case to provide a set of deprecations for the class and all its methods and properties in order to define fully automatic way to replace the class and all its usages?