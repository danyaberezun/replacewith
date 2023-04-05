# `ReplaceWith` inspection specification

* **Type**: TODO
* **Author**: TODO
* **Status**: TODO
* **Prototype**: TODO

- [`ReplaceWith` inspection specification](#replacewith-inspection-specification)
- [The story](#the-story)
  - [Feature use-cases](#feature-use-cases)
- [The problem](#the-problem)
- [Proposed `replaceWith` inspection specification](#proposed-replacewith-inspection-specification)
    - [Pros:](#pros)
    - [Cons:](#cons)
- [Examples](#examples)
    - [Let ''out from nowhere''](#let-out-from-nowhere)
    - [Argument initialization](#argument-initialization)
    - [Function with extension receiver](#function-with-extension-receiver)
    - [Replace function call with an import](#replace-function-call-with-an-import)
- [Corner cases](#corner-cases)
  - [Corner case: inlining is impossible](#corner-case-inlining-is-impossible)
  - [Corner case: ''separate/conflicting'' class name and methods replacements](#corner-case-separateconflicting-class-name-and-methods-replacements)
  - [Corner case: replace the class name and constructors](#corner-case-replace-the-class-name-and-constructors)
  - [Corner case: property and method name collision](#corner-case-property-and-method-name-collision)
- [`ReplaceWith` expression analysis, inspections, and IDE integration](#replacewith-expression-analysis-inspections-and-ide-integration)
- [Current bugs \& proposals](#current-bugs--proposals)
  - [$\\bullet$ Incorrect expression](#bullet-incorrect-expression)
  - [$\\bullet$ KTIJ-13679 @Deprecated ReplaceWith method does not convert to a property](#bullet-ktij-13679-deprecated-replacewith-method-does-not-convert-to-a-property)
  - [$\\bullet$ KTIJ-16495 Support `ReplaceWith` for constructor delegation call](#bullet-ktij-16495-support-replacewith-for-constructor-delegation-call)
  - [$\\bullet$ KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors](#bullet-ktij-6112-replacewith-quickfix-is-not-suggested-for-property-accessors)
  - [$\\bullet$ typealiases](#bullet-typealiases)
  - [$\\bullet$ KTIJ-11679 ReplaceWith replacing method call to property set ends up removing the line of code](#bullet-ktij-11679-replacewith-replacing-method-call-to-property-set-ends-up-removing-the-line-of-code)
  - [$\\bullet$ KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors](#bullet-ktij-6112-replacewith-quickfix-is-not-suggested-for-property-accessors-1)
  - [$\\bullet$ KTIJ-12396 Deprecated ReplaceWith quickfix with unaryPlus removes the line of code](#bullet-ktij-12396-deprecated-replacewith-quickfix-with-unaryplus-removes-the-line-of-code)
- [Discussion](#discussion)
- [Possible Future Work](#possible-future-work)
  - [`ReplaceWith` ancestors for specific use-cases](#replacewith-ancestors-for-specific-use-cases)
  - [`ReplaceWith` body analysis and Concrete syntax suggestion](#replacewith-body-analysis-and-concrete-syntax-suggestion)
- [Related work](#related-work)

# The story

As a project or library evolves, its API may significantly change.
`@Deprecated` annotation is widely used to encourage users to utilize the updated API or even more restrict some outdated API parts and force users to utilize the updated API.
In order to minimize overheads and speed up the migration process in $\texttt{Kotlin}$ `@Deprecated` annotations may be equipped with `ReplaceWith` annotation property which defines a code fragment that shall be used instead of the deprecated entity.
The simplest example is as follows.

``` Kotlin
@Deprecated("Use f() instead", replaceWith = ReplaceWith("f()"))
fun g(): Int = TODO()

fun h () {
    g̶() // <- point of interest
}
```

IDE proposes to apply a quick-fix and replace the call of outdated function `g̶()` with `f()`.

## Feature use-cases

The most common ways of utilizing `ReplaceWith` are the following

1. Semi-automatic library migration by updating API and saving the old interface at least temporarily.
2. Replace one class (name) with another.
3. Provide an IDE-guided way to learn a library's API by providing a deprecated API, which looks like something well-known by users, i.e., like it is done with the (well-known) Flow library in
   [kotlinx.couritines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/flow/Migration.kt).
4. Having some Java library, one has implemented a Kotlin-idiomatic API for it; in this case, `ReplaceWith` can be used to force and/or help Kotlin users to use this API.

# The problem

The current `replaceWith` implementation has no specification, only a brief description in [API](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-replace-with/), a set of [tests]([id-com-tests](https://github.com/JetBrains/intellij-community/tree/master/plugins/kotlin/idea/tests/testData/quickfix/deprecatedSymbolUsage)), and several blog posts ([1](https://dev.to/mreichelt/the-hidden-kotlin-gem-you-didn-t-think-you-ll-love-deprecations-with-replacewith-3blo),
[2](https://www.baeldung.com/kotlin/deprecation),
[3](https://todd.ginsberg.com/post/kotlin/deprecation/),
[4](https://readyset.build/kotlin-deprecation-goodies-a35a397aa9b5)) from users.
Moreover, its behaviour seems to be quite unexpected in some cases.
There is a set of tickets stating that `ReplaceWith` quick-fix simply removes the line of code ([KTIJ-12396](https://youtrack.jetbrains.com/issue/KTIJ-12396/Deprecated-ReplaceWith-quickfix-with-unaryPlus-removes-the-line-of-code),
[KTIJ-11679](https://youtrack.jetbrains.com/issue/KTIJ-11679/ReplaceWith-replacing-method-call-to-property-set-ends-up-removing-the-line-of-code),
[KTIJ-10798](https://youtrack.jetbrains.com/issue/KTIJ-10798/Deprecated-ReplaceWith-Constant-gets-replaced-with-nothing),
[KTIJ-13679](https://youtrack.jetbrains.com/issue/KTIJ-13679/Deprecated-ReplaceWith-method-does-not-convert-to-a-property)
) or doesn't work with properties ([KTIJ-12836](https://youtrack.jetbrains.com/issue/KTIJ-12836/ReplaceWith-cannot-replace-function-invocation-with-property-assignment),
[KTIJ-6112](https://youtrack.jetbrains.com/issue/KTIJ-6112/ReplaceWith-quickfix-is-not-suggested-for-property-accessors)
), and others ([KTIJ-22042](https://youtrack.jetbrains.com/issue/KTIJ-22042/IDE-Go-to-declaration-navigates-to-the-wrong-deprecated-maxBy-function),
[KTIJ-24906](https://youtrack.jetbrains.com/issue/KTIJ-24906/ReplaceWith-doesnt-add-several-imports)).
Moreover, some issues show that specification is needed since users can't get "what exactly" `ReplaceWith` does, [ex. here](https://stackoverflow.com/questions/55101974/kotlin-deprecated-annotation-replaces-this-with-class-name/72799974#72799974).
Thus, it's reasonable to provide a simple feature specification and fix its implementation according to the specification.
Most of the tickets above or similar simplified examples are considered [below](#current-bugs--proposals) in the context of the proposed specification.

# Proposed `replaceWith` inspection specification

1. *Behaviour for functions, methods, and constructors (FMC).* <br />
    Consider the replacement expression as a new body of the function/method/constructor (FMC), then inline on a call site.
    + In case the FMC replacement expression is just a name, i.e., `A::f`, treat it as a shortcut for a call `A::f(<args>)` where `args` are the same as in FMC call.
    + This also means that all arguments (by name), `this` and as well as access to properties and methods through it are available inside the `ReplaceWith` expression just like in a usual method's body.
    + Access to all identities from new `ReplaceWith` imports is the same as if they were imported in the standard way.
2. *Behaviour for classes.* <br />
    First, it is assumed that in the case of classes replacing one class name with another existing class only is possible.
    The expected behaviour is just to replace the old class name with a new class name keeping the context (class paths).
    Constructors are assumed to also be `ReplacedWith` new class constructors by replacing the class name only.
    NB, this case is error-prone.
3. *Property/variable/field replacement.* <br />
   1. *Replace with another property/var/field.* <br />
    Straightforward replacement (Of cause it is assumed that the inliner is able to distinguish `get` and `set`).
   2. *`get` and/or `set` replacement.* <br />
    Straightforward, the same as #1.
    Also, see [a current bug](#bullet-ktij-6112-replacewith-quickfix-is-not-suggested-for-property-accessors-2)

### Pros:
1. *Simplicity*. The specification is quite simple, easy to describe, and, what is most important, easy to understand by users.
2. The replacement expression is a *usual valid Kotlin code*; thus it can be analysed as any other code and checked for errors.
3. It seems to be that most of the use cases should be already covered by the inliner.

### Cons:
1. It narrows down the problem of `replaceWith` implementation to inlining; **maybe** this is asking too much of the inliner.
2. The proposed `replaceWith` specification is indeed quite simple.
But it requires a user to understand how inliner works.
This observation rises an interesting question:
``**Does the inliner have a normal specification**?''

# Examples

For the sake of clarity in this section we illustrate `ReplaceWith` usage by the set of examples.

### [Let ''out from nowhere''](https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/idea/tests/testData/quickfix/deprecatedSymbolUsage/argumentSideEffects/complexExpressionNotUsedSafeCall.kt)

``` Kotlin
class C {
    @Deprecated("", ReplaceWith("newFun()"))
    fun oldFun() {
        newFun()
    }
}

fun newFun() { TODO () }

fun getC(): C? = null

fun foo() {
    getC()?.o̶l̶d̶F̶u̶n̶() // <- point of interest
}
```

Since we use the safe-call operator `.?` here, after the replacement of `oldFun` call we have to call `newFun` if and only if the result of `getC` is not `null`.
Unlike `oldFun`, `newFun` is not a member of class `C`.
Thus, straightforward name replacement is impossible.
Instead, `ReplaceWith` inspection introduces a `let`-scope and results in `getC()?.let { newFun() }`.

Current behaviour and the expected behaviour coincide and correspond to the proposed specification.

### Argument initialization

``` Kotlin
package ppp
fun bar(): Int = 0
@Deprecated("", ReplaceWith("newFun()"))
fun oldFun(p: Int = ppp.bar()) {
    newFun()
}
fun newFun(){}
fun foo() {
    o̶l̶d̶F̶u̶n̶() // <- point of interest
}
```

According to the proposed specification during replacement arguments initialization should be preserved. I.e.
```
    bar()
    newFun()
```
Explanation: since we consider the expression as a new body of `oldFun`, inliner should call `bar` here (it may have side effects).
In this particular case, there is no sense to call `bar` but then we expect the inliner to use some kind of static analysis to decide this.
AFAIK, the inliner is not able to handle such optimization (and it is too computationally expensive).

Current behaviour and the expected behaviour coincide and correspond to the proposed specification.

### Function with extension receiver

``` Kotlin
fun test() {
    KotlinAPI().f̶o̶o̶() // <- point of interest
}

class KotlinAPI {
    fun bar() { TODO () }
}

fun bar() { TODO () }

@Deprecated(
    message = "deprecated",
    replaceWith = ReplaceWith(expression = "this.bar()") // <- point of interest
)
fun KotlinAPI.foo() { TODO () }
```

No matter if `this.bar()`, `this.bar`, `bar()`, or `bar` is used in the `ReplaceWith` expression, it is expected that `KotlinAPI().f̶o̶o̶()` will be replaced with `KotlinAPI().bar()`.
This behaviour corresponds to the proposed specification.

Current behaviour works as expected with `this.bar()` and `bar()`
and just removes function call in case of `this.bar` and `bar`.

### Replace function call with an import

``` Kotlin
object Math {
    @Deprecated("replace", ReplaceWith("kotlin.math.cos(x)", "kotlin.math.cos"))
    fun cos(x: Double): Double = kotlin.math.cos(x)
}

val test = Math.c̶o̶s̶(kotlin.math.PI) // <- point of interest
```

As a of the replacement,  `import kotlin.math.cos` should be added to the list of file imports while the `Math.cos(kotlin.math.PI)` call should be replaced with `cos(kotlin.math.PI)`.

Current behaviour and the expected behaviour coincide and correspond to the proposed specification.

# Corner cases

The section contains a description of some corner cases.

## Corner case: inlining is impossible

In case the inlining is impossible it is suggested to cancel the inspection with a corresponding error.
Analysis of ReplaceWith possibility, i.e. that inlining is possible in any usage scenario seems to be an amazing feature but is assumed to be too complex and expansive in the current proposal.

$\bullet$ Sometimes inlining is impossible. For example:
``` Kotlin
fun testT2(x: X) {
    x.o̶l̶d̶F̶u̶n̶X̶() // <- point of interest
}
class X {
    @Deprecated("", ReplaceWith("newFunX()"))
    fun oldFunX() {}
    private fun newFunX() {}
}
fun newFunX() {}
```

The expected behaviour is to replace `x.oldFunX()` with `x.newFunX()` since `newFunX()` corresponds to `this.newFunX()` in the usual Kotlin code.
However, since method `newFunX` is private.

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
    x.f̶() // <- point of interest
}
```

In this example, inlining is impossible.
It converts to ```x.set(x.get())``` which has a type Type mismatch, Required: Nothing
Found: Any?.
The reason is that inliner is not able to understand that two types are equal, the correct code is ```x.let {it.set(it.get() as Nothing)}```

## Corner case: ''separate/conflicting'' class name and methods replacements

Simplest case
``` Kotlin
@Deprecated("", replaceWith = ReplaceWith("B"))
class A () {
    @Deprecated("", ReplaceWith("newFun()"))
    fun oldFun() { TODO () }
}

var a = A̶().o̶l̶d̶F̶u̶n̶() // <- point of interest
```

In the current proposal, it is assumed that these two deprecations are two separate inspections.
Thus, no conflict here, it is controlled by the user which scenario to choose.
Scenarios:
1. First, replace `oldFun` with `newFun`, then replace class name getting as the result `B.newFun()`
2. Replace class name `A` with `B` getting as the result `A.oldFun()`. The case is error prone as well as class name replacement in general.

## Corner case: replace the class name and constructors

``` Kotlin
@Deprecated("", ReplaceWith("A"))
class OldClass1 @Deprecated("", ReplaceWith("B(0)")) constructor()

val a = O̶l̶d̶C̶l̶a̶s̶s̶1̶() // <- point of interest #1

@Deprecated("", ReplaceWith("A"))
class OldClass2 constructor(<...>) { TODO () }

val a = O̶l̶d̶C̶l̶a̶s̶s̶2̶(<...>) // <- point of interest #2

@Deprecated("", ReplaceWith("A"))
class OldClass3

val a = O̶l̶d̶C̶l̶a̶s̶s̶3̶() // <- point of interest #3
```

`ReplaceWith` inspection has to be aware of whether the class being deprecated has deprecations on its primary and secondary constructors.
If a constructor has its own deprecation annotation with `ReplaceWith`, any of its calls can be only replaced according to this annotation but not the class annotation.
If a constructor does not have its own deprecation annotation, all of its calls have to be replaced according to the class deprecation annotation.

Expected and current behaviours coincide: </br>
#1 to be replaced with `B(0)` </br>
#2 is replaced with `A(<...>)` </br>
#3 to be replaced with `A()`

## Corner case: property and method name collision

When replacing one method with another, it is often the case that `ReplaceWith` expression is just a name of a method that a call should be replaced with.
The ambiguity occurs if "new" method has the same name as some property.

``` Kotlin
class A {
    @Deprecated("", replaceWith = ReplaceWith("A.foo"))
    fun f (<f_args>) { TODO () }

    var foo : Int = 0

    fun foo (<...>) { TODO () }
}

a.f() // <- point of interest
```

In this case, if a property has to be used instead, the `ReplaceWith` expression ought to be enclosed in parentheses.
In the example above `(A.foo)`.
Otherwise, it has to be treated as a function call, i.e. `A.foo(<f_args>)`.

Note, the ambiguity only occurs if and only if `ReplaceWith` expression is just a name and yet it is ambiguous.

# `ReplaceWith` expression analysis, inspections, and IDE integration

1. `ReplaceWith` expression analysis. </br>
    It is necessary to provide syntax highlighting, code completion, and code analysis, i.e. *all usual inspections* and error checks for `ReplaceWith` expression as it is for any other code.
2. It is expected that other inspections are aware of the code in `ReplaceWith` expressions.
    For example, it means that `findUsages` should also point to code inside `ReplaceWith` expression, `rename` should also rename identities inside a `ReplaceWith` body, etc.
3. *typealiases* </br>
    Typealiases should be treated as one type (nothing specific to the concrete inspection), i.e. the `ReplaceWith` inspection has to be aware of them.


# Current bugs \& proposals

## $\bullet$ Incorrect expression

In case of an incorrect expression or any other fail `replaceWith` just removes the expression to be replaced.

Simplest example:

Assuming `b` is undefined:
``` Kotlin
@Deprecated(message = "", replaceWith = ReplaceWith("b"))
fun f() { 1 }

fun test () { f̶() } // <- point of interest
```

The current behaviour is just to remove the call:
`fun test () {}`.

The expected behaviour is to show the error in `replaceWith` expression and either perform no replacement at all while the expression is not error-free or provide some kind of error during the replacement.

## $\bullet$ [KTIJ-13679 @Deprecated ReplaceWith method does not convert to a property](https://youtrack.jetbrains.com/issue/KTIJ-13679/Deprecated-ReplaceWith-method-does-not-convert-to-a-property)

``` Kotlin
@Deprecated("Nice description here", replaceWith = ReplaceWith("isVisible = visible"))
fun C.something(visible: Boolean)  { TODO () }

class C() { var isVisible: Boolean = false }

fun use() {
    C().s̶o̶m̶e̶t̶h̶i̶n̶g̶(false) // <- point of interest
}
```

Expected: `C().isVisible = false`

Current behaviour: `C()`

The proposed specification corresponds to the expected one.

## $\bullet$ [KTIJ-16495 Support `ReplaceWith` for constructor delegation call](https://youtrack.jetbrains.com/issue/KTIJ-16495/Support-ReplaceWith-for-constructor-delegation-call)

``` Kotlin
open class A(val s: String, val i: () -> Int, val i2: Int) {
    @Deprecated("Replace with primary constructor", ReplaceWith("A(s = \"\", a = { i }, m = i)"))
    constructor(i: Int) : this("", { i }, i)
}

class T: A {
    constructor(): s̶u̶p̶e̶r̶(33)      // <- point of interest
    constructor(i: Int): s̶u̶p̶e̶r̶(i) // <- point of interest
}
```

Current behaviour shows that `super` with one `Int` argument is deprecated but no `ReplaceWith` is possible.

Expected behaviour is to replece them with `constructor() : super ("", { 0 }, 0)` and `constructor(i: Int) : super("", { i }, i)` correspondingly.
The expected behaviour corresponds to the proposed specification.

## $\bullet$ [KTIJ-6112 ReplaceWith quickfix is not suggested for property accessors](https://youtrack.jetbrains.com/issue/KTIJ-6112/ReplaceWith-quickfix-is-not-suggested-for-property-accessors)

``` Kotlin
var deprecated: Int = 42
    @Deprecated("", ReplaceWith("other"))
    get
    @Deprecated("", ReplaceWith("other = value"))
    set

var other = 33

fun test() {
    println(deprecated) // <- point of interest
    deprecated = 33     // <- point of interest
}
```
Expected: </br>
#1 to be replaced with `println(other)` </br>
#2 to be replaced with `other = 33`

Currently, `ReplaceWith` quick-fix is not suggested for property accessors but it should and the expected behaviour corresponds to the proposed specification.

## $\bullet$ typealiases

``` Kotlin
@Deprecated("", ReplaceWith("NewClass"))
class OldClass @Deprecated("", ReplaceWith("NewClass(12)")) constructor()

class NewClass(p: Int = 0)

typealias Old = OldClass // <- point of interest #1

val a = Old() // -> NewClass(12) // <- point of interest #2
```

It is expected to replace `OldClass` with `NewClass` as class names at point #1, and `Old()` with `NewClass(12)` at point #2.

The current behaviour is correct at point #2 but fails at point #1 since it tries to replace it as a constructor.

Expected behaviour corresponds to the proposed specification.

## $\bullet$ [KTIJ-11679 ReplaceWith replacing method call to property set ends up removing the line of code](https://youtrack.jetbrains.com/issue/KTIJ-11679/ReplaceWith-replacing-method-call-to-property-set-ends-up-removing-the-line-of-code)

``` Kotlin
@Deprecated("", ReplaceWith("new = value"))
fun Int.old(value: Int) = Unit
var Int.new: Int
    get() = 0
    set(value) = Unit

fun aFunction() {
    1.old(0) // <- point of interest
}
```
Currently, it doesn't work now but it should, and the expected behaviour corresponds to the proposed specification.

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
    c.property // <- point of interest #1
    c.property = c.property // <- point of interest #2
}
```
Expected:</br>
#1 to be replaced with `c.function ()`</br>
#2 to be replaced with `c.function (c.function())`

Currently, it doesn't work now but it should, and the expected behaviour corresponds to the proposed specification.

## $\bullet$ [KTIJ-12396 Deprecated ReplaceWith quickfix with unaryPlus removes the line of code](https://youtrack.jetbrains.com/issue/KTIJ-12396/Deprecated-ReplaceWith-quickfix-with-unaryPlus-removes-the-line-of-code)

``` Kotlin
@Deprecated("", ReplaceWith("+b"))
fun foo(b: Bar) {}

class Bar {
    operator fun unaryPlus() {}
}

fun test() {
    val b = Bar()
    println(foo(b)) // <- point of interest #1
    foo(b)          // <- point of interest #2
}
```

Expected:</br>
#1 to be replaced with `println(+b)` </br>
#2 to be replaced with `+b`

Current behaviour:</br>
#1 is replaced with `println(+b)` </br>
#2 is replaced with empty string

The expected behaviour corresponds to the proposed specification.

# Discussion

1. Should ReplaceWith replace constructors when replacing the class name?
2. Even more, the question is **When should we suggest class name replacement?** if it is specified with `replaceWith`. <br/>
    In my opinion, class name replacement is a distinct case but users use it, so we have to specify the behaviour clearly and its connection with ``possibly conflicting'' methods replacements or even lead to errors.
3. What is the expected IDE behaviour when ReplaceWith leads to an error (in the resulting code)?
    What if replacing all get errors in some cases?
4. Can we provide an analysis that checks the correctness of a potential substitution? For example, show an error at the location of concrete ReplaceWith definition if any of its usages would result in an error.
5. The connection between different deprecations.
    Consider, for example, again a [corner case: ''separate/conflicting'' class name and methods replacements](#corner-case-separateconflicting-class-name-and-methods-replacements) or [feature use-case \#3](#feature-use-cases).
    Could it be the case to provide a set of deprecations for the class and all its methods and properties in order to define a fully automatic way to replace the class and all its usage?
6. How should `ReplaceWith` relate to `HIDDEN` deprecation level? </br>
    + `ReplaceWith` should be out f reach in case of `HIDDEN`?

# Possible Future Work

## `ReplaceWith` ancestors for specific use-cases

1. [KT-56969 Use body of deprecated function instead of ReplaceWith()](https://youtrack.jetbrains.com/issue/KT-56969/Use-body-of-deprecated-function-instead-of-ReplaceWith) <br/>
    The issue contains a special case of the `ReplaceWith` usage when the body of a deprecated method and `ReplaceWith` expression are identical.
    It looks like just forcing the inlining.
    I guess it can be supported by either introducing `$body$` to refer to the existing body or `ReplaceWithBody()`, i.e. a successor of `ReplaceWith`.
2. In case of an empty ReplaceWith expression (or new code scope is empty, or something like `ReplaceWithRemove()` depending on concrete syntax), the call should be removed.
    Connected example: [KTIJ-8601 ReplaceWith: provide mechanism for removing the function call](https://youtrack.jetbrains.com/issue/KTIJ-8601/ReplaceWith-provide-mechanism-for-removing-the-function-call)
    ``` Kotlin
    fun <T> Collection<T>.unique(): Set<T> = toSet()
    @Deprecated("Useless",
        replaceWith = ReplaceWith("this")
        // or ReplaceWith(""), or ReplaceWithRemove(), or ReplaceWith() { }
    )
    fun <T> Set<T>.unique() = this
    fun test () {
        setOf(1).u̶n̶i̶q̶u̶e̶().joinToString() // <- point of interest
    }
    ```
    Expected result: `setOf(1).joinToString()`

## `ReplaceWith` body analysis and Concrete syntax suggestion

Currently, `ReplaceWith` expression is not analysed at all.
I propose to implement a separate IDE *Injection* which makes it possible to analyse `ReplaceWith` body.
I.e. the expression will be stored as a usual expression but appear to a user as usual Kotlin code.
I suggest concrete syntax to be an explicit scope instead of expression. I.e. something like
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

First, it's more clear than the current syntax. Second, I assume if it is possible to make a scope a part of AST (PSI) then the previous point will be supported ``for free''.

# Related work

One can think of `ReplaceWith` inspection as a lightweight incremental way to evolve APIs.
It is less expressive and powerful than a full-fledged migration tool.
Thus, any comparison with such tools has no sense at all.
Thus, one can either implement a complete migration tool or emulate `ReplaceWith` functionality with a set of custom IDE inspections.
Both approaches are much more difficult and error-prone.

In $\texttt{Rider}$ similar to `ReplaceWith` functionality [can be implemented with `[CodeTemplate]` attribute](https://www.jetbrains.com/help/rider/Code_Analysis__Find_and_Update_Obsolete_APIs.html): (c) </br>
*As the API author, you need to mark the obsolete type or member with the [CodeTemplate] attribute from JetBrains.Annotations where you can specify a search pattern to match the old API and a replacement pattern for it.*
Theoretically, the approach seems to be more powerful but at the same time more complex, less transparent, and not too far from custom inspection implementation.