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
`@Deprecated` annotation is widely used to encourage users to utilize the updated API or, even, restrict some outdated API and force users to utilize the updated API.
In order to minimize overhead and speed up the migration process in $\texttt{Kotlin}$ `@Deprecated` annotations may be equipped with `ReplaceWith` annotation property which defines a code fragment that should be used instead of the deprecated entity.
The simplest example is as follows.

``` Kotlin
@Deprecated(message = "Use f() instead", replaceWith = ReplaceWith("f()"))
fun g(): Int = TODO()

fun h () {
    g̶() // <- point of interest
}
```

IDE proposes to apply a quick-fix and replace the call of the outdated function `g̶()` with `f()`.

## Feature use-cases

The most common ways of utilizing `ReplaceWith` are the following.

1. Semi-automatic library migration by updating API and saving the old interface temporarily.
2. Replace one class (name) with another.
3. Provide an IDE-guided way to learn a library's API by providing a deprecated API, which resembles something well-known by users, i.e. as it is done with the (well-known) Flow library in
   [kotlinx.couritines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/flow/Migration.kt).
4. If there is a Kotlin-idiomatic API for some Java library, `ReplaceWith` can be used to force and/or help Kotlin users to use this API.

# The problem

The current `replaceWith` implementation has no specification, only a brief description in [API](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-replace-with/), a set of [tests]([id-com-tests](https://github.com/JetBrains/intellij-community/tree/master/plugins/kotlin/idea/tests/testData/quickfix/deprecatedSymbolUsage)), and several blog posts ([1](https://dev.to/mreichelt/the-hidden-kotlin-gem-you-didn-t-think-you-ll-love-deprecations-with-replacewith-3blo),
[2](https://www.baeldung.com/kotlin/deprecation),
[3](https://todd.ginsberg.com/post/kotlin/deprecation/),
[4](https://readyset.build/kotlin-deprecation-goodies-a35a397aa9b5)) from users.
Moreover, its behaviour is rather unexpected in some cases.
There are tickets stating that `ReplaceWith` quick-fix simply removes the line of code ([KTIJ-12396](https://youtrack.jetbrains.com/issue/KTIJ-12396/Deprecated-ReplaceWith-quickfix-with-unaryPlus-removes-the-line-of-code),
[KTIJ-11679](https://youtrack.jetbrains.com/issue/KTIJ-11679/ReplaceWith-replacing-method-call-to-property-set-ends-up-removing-the-line-of-code),
[KTIJ-10798](https://youtrack.jetbrains.com/issue/KTIJ-10798/Deprecated-ReplaceWith-Constant-gets-replaced-with-nothing),
[KTIJ-13679](https://youtrack.jetbrains.com/issue/KTIJ-13679/Deprecated-ReplaceWith-method-does-not-convert-to-a-property)
) or does not work with properties ([KTIJ-12836](https://youtrack.jetbrains.com/issue/KTIJ-12836/ReplaceWith-cannot-replace-function-invocation-with-property-assignment),
[KTIJ-6112](https://youtrack.jetbrains.com/issue/KTIJ-6112/ReplaceWith-quickfix-is-not-suggested-for-property-accessors)
), and others ([KTIJ-22042](https://youtrack.jetbrains.com/issue/KTIJ-22042/IDE-Go-to-declaration-navigates-to-the-wrong-deprecated-maxBy-function),
[KTIJ-24906](https://youtrack.jetbrains.com/issue/KTIJ-24906/ReplaceWith-doesnt-add-several-imports)).
Moreover, some issues show that specification is needed since users do not understand "what exactly" `ReplaceWith` does, [ex. here](https://stackoverflow.com/questions/55101974/kotlin-deprecated-annotation-replaces-this-with-class-name/72799974#72799974).
Thus, it's reasonable to provide a simple feature specification and fix the implementation according to the specification.
Most of the tickets mentioned are discussed [below](#current-bugs--proposals) in the context of the proposed specification, sometimes with simplified examples.

# Proposed `replaceWith` inspection specification

1. *Behaviour for functions, methods, and constructors (FMC).* <br />
    Regard the replacement expression as a new body of the function/method/constructor (FMC), then inline on a call site, as illustrated by the following example.

    ```Kotlin
@Deprecated(
    message = "old is deprecated, use new instead",
    replaceWith = ReplaceWith(expression = "new(x, y, x*y)")
)
fun old(x : Int, y : Int) { TODO () }

fun new(x : Int, y : Int, product : Int) { TODO () }

fun foo() {
    o̶l̶d̶(7, 42) // -> new(7, 42, 7*42)
}
    ```

    In this example there is the function `old` with two arguments `x` and `y` which is replaced with a call to the function `new` with three arguments.
    Note that the replacement expression passes the arguments of the `old` function to the `new` function and also uses their product as the third argument.
    Applying the replaceWith inspection corresponds to considering the replacement expression to be a body of the deprecated function: `fun old(x : Int, y : Int) {new(x, y, x*y)}` and then inlining its body on call site.
    Thus, the call `old(7, 42)` is replaced with `new(7, 42, 7*42)`, its arguments are correctly passed.

    Besides using the arguments of the deprecated function, it is also allowed to use `this` as well as properties and methods, accessible in the deprecated function.
    Specifying the list of imports in the replaceWith grants access to identifiers from them.

    In case the FMC replacement expression is just a name, i.e., `A::f`, treat it as a shortcut for a call `A::f(<args>)` where `args` are the same as in FMC call.

2. *Behaviour for classes.* <br />
    It is assumed that in the case of replacing one class name with another, only the use of an existing class is permitted.
    The expected behaviour is to replace the old class name with the new class name keeping the context (class paths).
    Constructors are to be replaced with the new class constructors by replacing the class name only.
    NB, this case is error-prone.
3. *Property/variable/field replacement.* <br />
   1. *Replace with another property/var/field.* <br />
    Straightforward replacement (Of cause it is assumed that the inliner is able to distinguish `get` and `set`).
   2. *`get` and/or `set` replacement.* <br />
    Straightforward, the same as #1.
    Also, see [a current bug](#bullet-ktij-6112-replacewith-quickfix-is-not-suggested-for-property-accessors-2)

### Pros:
1. *Simplicity*. The specification is quite simple, easy to describe, and, most importantly, easy to understand by users.
2. The replacement expression is a *normal valid Kotlin code*, thus it can be analysed and checked for errors.
3. Most of the use cases should be already covered by the inliner.

### Cons:
1. It narrows down the problem of `replaceWith` implementation to inlining; **maybe** this is asking too much of the inliner.
2. The proposed `replaceWith` specification is indeed quite simple.
However it requires a user to understand how inliner works.
This observation rises an interesting question:
``**Does the inliner have a good specification**?''

# Examples

For the sake of clarity, we illustrate `ReplaceWith` usage in more complicated cases by the set of examples.

### [Let ''out from nowhere''](https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/idea/tests/testData/quickfix/deprecatedSymbolUsage/argumentSideEffects/complexExpressionNotUsedSafeCall.kt)

``` Kotlin
class C {
    @Deprecated("", ReplaceWith("new()"))
    fun old() {
        new()
    }
}

fun new() { TODO () }

fun getC(): C? = null

fun foo() {
    getC()?.o̶l̶d̶() // -> getC()?.let { new() }
}
```

Here the method `old` is called by using the safe-call operator `.?`, thus the call to `new` after the replacement should be done only if the result of `getC` is not `null`.
Unlike `old`, `new` is not a member of class `C`, thus straightforward name replacement is impossible.
Instead, `ReplaceWith` inspection introduces a `let`-scope and results in `getC()?.let { new() }`.

The current behaviour and the expected behaviour coincide and correspond to the proposed specification.

### Argument initialization

``` Kotlin
package ppp

fun bar(): Int = 0

@Deprecated("", ReplaceWith("new()"))
fun old(p: Int = ppp.bar()) {
    new()
}

fun new(){}

fun foo() {
    o̶l̶d̶() // <- point of interest
}
```

According to the proposed specification, arguments initialization should be preserved during the replacement, thus replacement of the call results in the following `foo` definition:

```
fun foo() {
    bar()
    new()
}
```

Since we consider the expression as the new body of `old`, inliner should call `bar` here because it may have some side effects.
In this particular case, there is no reason to call `bar` but then the inliner should use some kind of static analysis to decide this.
AFAIK, the inliner is not able to handle such optimization, and it may also be too computationally expensive.

The current behaviour and the expected behaviour coincide and correspond to the proposed specification.

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

No matter if `this.bar()`, `this.bar`, `bar()`, or `bar` is used in the `ReplaceWith` expression, it is expected that `KotlinAPI().f̶o̶o̶()` is replaced with `KotlinAPI().bar()`.
This behaviour corresponds to the proposed specification.

The current behaviour works as expected with `this.bar()` and `bar()`
but removes function call in case of `this.bar` and `bar`.

### Replace function call with an import

``` Kotlin
object Math {
    @Deprecated(
        message: "deprecated",
        ReplaceWith(expression: "kotlin.math.cos(x)", imports: "kotlin.math.cos"))
    fun cos(x: Double): Double = kotlin.math.cos(x)
}

val test = Math.c̶o̶s̶(kotlin.math.PI) // <- point of interest
```

In this replacement, `import kotlin.math.cos` is added to the list of file imports while the `Math.cos(kotlin.math.PI)` call is replaced with `cos(kotlin.math.PI)`.

The current behaviour and the expected behaviour coincide and correspond to the proposed specification.

# Corner cases

The section contains a description of some corner cases.

## Corner case: inlining is impossible

In case when the inlining is impossible, it is suggested to cancel the inspection with a corresponding error.
Analysis of ReplaceWith possibility, i.e. that the inlining is possible in any usage scenario, seems to be an amazing feature but is assumed to be too complex and expensive in the current proposal.

$\bullet$ Sometimes inlining is impossible. For example:
``` Kotlin
fun testT2(x: X) {
    x.o̶l̶d̶X̶() // <- point of interest
}

class X {
    @Deprecated("", ReplaceWith("newX()"))
    fun oldX() {}
    private fun newX() {}
}

fun newX() {}
```

The expected behaviour is to replace `x.oldX()` with `x.newX()` since `newX()` corresponds to `this.newX()` in Kotlin code.
However, the method `newX` is private, and cannot be accessed at the call site.

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

Inlining in this case results with ```x.set(x.get())``` which has a type Type mismatch
```
Required: Nothing
Found: Any?.
```
The reason is that the inliner is not able to understand that two types are equal.
The correct code is ```x.let {it.set(it.get() as Nothing)}```

## Corner case: ''separate/conflicting'' class name and methods replacements

Simplest case
``` Kotlin
@Deprecated("", replaceWith = ReplaceWith("B"))
class A () {
    @Deprecated("", ReplaceWith("new()"))
    fun old() { TODO () }
}

var a = A̶().o̶l̶d̶() // <- point of interest
```

In the current proposal, these two deprecations are two separate inspections.
Thus, no conflict arises here, and the user controlles which scenario to choose.

Possible scenarios:
1. First, replace `old` with `new`, then replace the class name resulting in `B.new()`
2. Replace the class name `A` with `B` getting an intermediate result `B.old()`, where `old` is unresolved.

This case is error prone as the class name replacement in general.

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

`ReplaceWith` inspection has to be aware whether the primary and secondary constructors of the class being deprecated are also deprecated.
If a constructor is annotated as deprecated and has a `ReplaceWith` expression, any of its calls can only be replaced according to this annotation and not the class annotation.
If a constructor does not have its own deprecation annotation, all of its calls have to be replaced according to the class deprecation annotation.

The expected and the current behaviours coincide: </br>
#1 is replaced with `B(0)` </br>
#2 is replaced with `A(<...>)` </br>
#3 is replaced with `A()`

## Corner case: property and method name collision

When replacing one method with another, it is often the case that `ReplaceWith` expression is just a name of a method that a call should be replaced with.
The ambiguity occurs if the "new" method has the same name as a property of the class.

``` Kotlin
class A {
    @Deprecated("", replaceWith = ReplaceWith("A.foo"))
    fun f (<f_args>) { TODO () }

    var foo : Int = 0

    fun foo (<...>) { TODO () }
}

a.f() // <- point of interest
```

In this case, if a property has to be used, the `ReplaceWith` expression ought to be enclosed in parentheses, such as `(A.foo)` in the example.
Otherwise, it has to be treated as a function call, i.e. `A.foo(<f_args>)`.

Note, the ambiguity only occurs when `ReplaceWith` expression is just a name which is ambiguous.

# `ReplaceWith` expression analysis, inspections, and IDE integration

1. `ReplaceWith` expression analysis. </br>
    It is necessary to provide syntax highlighting, code completion, and code analysis, i.e. *all usual inspections* and error checks for `ReplaceWith` expression as it is done for any other code.
2. It is expected that other inspections are aware of the code in `ReplaceWith` expressions.
    For example, it means that `findUsages` should point to the code inside `ReplaceWith` expression, `rename` should rename inside a `ReplaceWith` body, etc.
3. *typealiases* </br>
    Typealiases should be treated as one type (nothing specific to the concrete inspection), i.e. the `ReplaceWith` inspection has to be aware of them.


# Current bugs \& proposals

## $\bullet$ Incorrect expression

Currently, if replacement fails, the expression to be replaced is silently removed.
The simplest example is the following.

Assuming `b` is undefined:

``` Kotlin
@Deprecated(message = "", replaceWith = ReplaceWith("b"))
fun f() { 1 }

fun test () { f̶() } // <- point of interest
```

The current behaviour removes the call to `f`:
`fun test () {}`.

The expected behaviour is to either show an error in the `replaceWith` expression and to perform no replacement at all, if the replacement expression contains errors, or to provide an error during the replacement.

## $\bullet$ [KTIJ-13679 @Deprecated ReplaceWith method does not convert to a property](https://youtrack.jetbrains.com/issue/KTIJ-13679/Deprecated-ReplaceWith-method-does-not-convert-to-a-property)

``` Kotlin
@Deprecated("deprecated", replaceWith = ReplaceWith("isVisible = visible"))
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

The current behaviour shows that `super` with one `Int` argument is deprecated but no `ReplaceWith` is possible.

The expected behaviour is to replace them with `constructor() : super ("", { 0 }, 0)` and `constructor(i: Int) : super("", { i }, i)` correspondingly.
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
    println(deprecated) // <- point of interest #1
    deprecated = 33     // <- point of interest #2
}
```
Expected: </br>
#1 is replaced with `println(other)` </br>
#2 is replaced with `other = 33`

Currently, `ReplaceWith` quick-fix is not suggested for property accessors, even though it should. The expected behaviour corresponds to the proposed specification.

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

The expected behaviour corresponds to the proposed specification.

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
Currently, it does not work, even though it should. The expected behaviour corresponds to the proposed specification.

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

Currently, it does not work, even though it should. The expected behaviour corresponds to the proposed specification.

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
#1 is replaced with `println(+b)` </br>
#2 is replaced with `+b`

Current behaviour:</br>
#1 is replaced with `println(+b)` </br>
#2 is replaced with empty string

The expected behaviour corresponds to the proposed specification.

# Discussion

1. Should ReplaceWith replace constructors when replacing the class name?
2. The more important question is **When should we suggest class name replacement?** if it is specified with `replaceWith`. <br/>
    In my opinion, class name replacement is a distinct case but users use it, so we have to specify the behaviour clearly and its connection with the ``possibly conflicting'' methods replacements or potential errors.
3. What is the expected IDE behaviour when ReplaceWith leads to an error in the resulting code?
    What if replacing all get errors only in some cases?
4. Is it possible to provide an analysis that checks the correctness of a potential replacement? For example, show an error at the location of concrete ReplaceWith definition if any of its usages would result in an error.
5. The connection between different deprecations.
    Consider, for example, again a [corner case: ''separate/conflicting'' class name and methods replacements](#corner-case-separateconflicting-class-name-and-methods-replacements) or [feature use-case \#3](#feature-use-cases).
    Should it be necessary to provide the set of deprecations for all methods and properties of the class in order to define a fully automatic way to replace the class and all its usages?
6. How should `ReplaceWith` relate to the `HIDDEN` deprecation level? </br>
    + `ReplaceWith` should be out of reach in case of `HIDDEN`?

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
In this case, the expression is stored as an expression but appear to a user as a normal Kotlin code.
I suggest concrete syntax to be an explicit scope instead of an expression, such as:

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

First, it's more clear than the current syntax. Second, it should be possible to make this scope a part of AST (PSI), and then the analyses may be achieved more easily.

# Related work

One can think of `ReplaceWith` inspection as a lightweight incremental way to evolve APIs.
It is less expressive and powerful than a full-fledged migration tool.
Any comparison with such tools does not make much sense.
Thus, one can either implement a complete migration tool or emulate `ReplaceWith` functionality with a set of custom IDE inspections.
Both approaches are significantly more complicated and error-prone.

In $\texttt{Rider}$ functionality similar to `ReplaceWith` [can be implemented with `[CodeTemplate]` attribute](https://www.jetbrains.com/help/rider/Code_Analysis__Find_and_Update_Obsolete_APIs.html): (c) </br>
*As the API author, you need to mark the obsolete type or member with the [CodeTemplate] attribute from JetBrains. Annotations where you can specify a search pattern to match the old API and a replacement pattern for it.*
This approach seems to be more powerful but more complex, less transparent, and that far from a custom inspection implementation.