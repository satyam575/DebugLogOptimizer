package org.debugBetter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;

public class DebugToGuardRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Guard debug logs with isDebugEnabled";
    }

    @Override
    public String getDescription() {
        return "Wraps SLF4J debug() calls with if (logger.isDebugEnabled()) guards.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {

            private final JavaTemplate guardTemplate =
                    JavaTemplate.builder(
                            "if (#{any(java.lang.Object)}.isDebugEnabled()) {\n" +
                            "    #{any(java.lang.Object)};\n" +
                            "}"
                    ).build();

            @Override
            public Statement visitStatement(Statement statement, ExecutionContext ctx) {
                Statement s = super.visitStatement(statement, ctx);
                if (!(s instanceof J.MethodInvocation mi)) {
                    return s;
                }

                if (!"debug".equals(mi.getSimpleName())) {
                    return s;
                }

                JavaType.Method methodType = mi.getMethodType();

                if (mi.getArguments().size() < 2) {
                    return s;
                }

                Expression select = mi.getSelect();
                if (select == null || !DebugRecipeUtils.isSafeLoggerSelect(select)) {
                    return s;
                }

                boolean isSlf4j = methodType != null && DebugRecipeUtils.isSlf4jLogger(methodType);
                boolean isLombokSlf4j = methodType == null
                        && DebugRecipeUtils.isLombokSlf4jAnnotated(this)
                        && DebugRecipeUtils.isLombokSlf4jLoggerSelect(select);
                if (!isSlf4j && !isLombokSlf4j) {
                    return s;
                }

                if (DebugRecipeUtils.isAlreadyGuarded(this)) {
                    return s;
                }

                return DebugRecipeUtils.applyGuard(this, guardTemplate, mi, select);
            }
        };
    }
}
