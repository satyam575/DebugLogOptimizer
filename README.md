# DebugLogOptimizer

OpenRewrite recipe that lazifies debug log arguments by wrapping non-trivial
expressions in a `Supplier` lambda so they are only evaluated when debug logging
is enabled.

## What it does

The recipe targets `debug(...)` calls with two or more arguments (message/template
plus arguments). It leaves the message argument unchanged and wraps non-trivial
arguments like method calls or object creation with `() -> expr`.

Examples:

```java
log.debug("user {}", getUser());
```

becomes:

```java
log.debug("user {}", () -> getUser());
```

Trivial arguments such as identifiers, field access, or literals are left as-is.

## Build

```bash
mvn -DskipTests package
```

Output JAR:

```
target/debug-optimizer-0.1.0.jar
```

## Use in IntelliJ (OpenRewrite plugin)

1. Install the OpenRewrite plugin in IntelliJ.
2. Add the built JAR in the OpenRewrite settings.
3. Run the recipe by name:

```
org.debugBetter.DebugOptimizer
```

## Recipe definition

The recipe is registered in:

```
src/main/resources/META-INF/rewrite/rewrite.yml
```

