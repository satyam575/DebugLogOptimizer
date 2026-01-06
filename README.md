# DebugLogOptimizer

OpenRewrite recipes for optimizing debug logging. The recipes are split by
strategy so you can pick the behavior you want.

## Recipes

### Wrap arguments with Supplier

Recipe: `org.debugBetter.DebugToSupplier`

Targets `debug(...)` calls on loggers that expose `Supplier` overloads (for
example, Log4j2). It leaves the message argument unchanged and wraps non-trivial
arguments in `() -> expr`.

Example:

```java
log.debug("user {}", getUser());
```

becomes:

```java
log.debug("user {}", () -> getUser());
```

### Guard with isDebugEnabled

Recipe: `org.debugBetter.DebugToGuard`

Targets SLF4J `debug(...)` calls and wraps them in a guard:

```java
if (log.isDebugEnabled()) {
    log.debug("debugging: {}", request);
}
```

### Convert to SLF4J fluent API

Recipe: `org.debugBetter.DebugToFluent`

Targets SLF4J 2.0+ and rewrites to the fluent API:

```java
log.atDebug()
   .setMessage("debugging: {}")
   .addArgument(request)
   .log();
```

## Build

```bash
mvn -DskipTests package
```

Output JAR:

```
target/debug-optimizer-0.1.0.jar
```

## Use via Maven plugin

Install the recipe to your local Maven repo:

```bash
mvn -DskipTests install
```

Run on a target project:

```bash
mvn -Drewrite.recipeArtifactCoordinates=com.debugBetter:debug-optimizer:0.1.0 ^
    -Drewrite.activeRecipes=org.debugBetter.DebugToSupplier ^
    org.openrewrite.maven:rewrite-maven-plugin:5.44.0:dryRun
```

Use `rewrite:run` to apply changes.

You can also configure the plugin in your target project's `pom.xml`:

```xml
<plugin>
  <groupId>org.openrewrite.maven</groupId>
  <artifactId>rewrite-maven-plugin</artifactId>
  <version>5.44.0</version>
  <configuration>
    <activeRecipes>
      <recipe>org.debugBetter.DebugToFluent</recipe>
    </activeRecipes>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>com.debugBetter</groupId>
      <artifactId>debug-optimizer</artifactId>
      <version>0.1.0</version>
    </dependency>
  </dependencies>
</plugin>
```

## Use via JitPack (public GitHub)

1. Make sure the GitHub repo is public.
2. Tag a release and push it, for example:

```bash
git tag v0.1.0
git push origin v0.1.0
```

3. In the target project, add the JitPack repository and dependency:

```xml
<repositories>
  <repository>
    <id>jitpack</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.satyam575</groupId>
    <artifactId>DebugLogOptimizer</artifactId>
    <version>v0.1.0</version>
  </dependency>
</dependencies>
```

Then reference the recipe ID, for example:

```
org.debugBetter.DebugToFluent
```

## Use in IntelliJ (OpenRewrite plugin)

1. Install the OpenRewrite plugin in IntelliJ.
2. Add the built JAR in the OpenRewrite settings (Recipe Sources / Classpath / Add JAR).
3. Run a recipe by name, for example:

```
org.debugBetter.DebugToSupplier
```

## Recipe definition

The recipe is registered in:

```
src/main/resources/META-INF/rewrite/rewrite.yml
```

## Notes

- `DebugToSupplier` requires a logger that supports `Supplier` overloads (for example, Log4j2).
- `DebugToGuard` targets SLF4J and wraps `debug(...)` with `if (logger.isDebugEnabled())`.
- `DebugToFluent` targets SLF4J 2.0+ and uses `atDebug()`. If type attribution is missing, it can also match Lombok `@Slf4j` with the generated `log` field.
- Type attribution must be available in the target project for logger matching to work.
- If type attribution is missing, `DebugToGuard` can still match Lombok `@Slf4j` by using the generated `log` field name.
