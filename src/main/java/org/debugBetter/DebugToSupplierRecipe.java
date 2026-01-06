package org.debugBetter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class DebugToSupplierRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Wrap debug arguments with Supplier";
    }

    @Override
    public String getDescription() {
        return "Wraps non-trivial debug() arguments in Supplier lambdas for loggers that support Supplier overloads.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {

            private final JavaTemplate supplierLambda =
                    JavaTemplate.builder("() -> #{any(java.lang.Object)}").build();

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                mi = super.visitMethodInvocation(mi, ctx);

                if (!"debug".equals(mi.getSimpleName())) {
                    return mi;
                }

                JavaType.Method methodType = mi.getMethodType();
                if (methodType == null) {
                    return mi;
                }

                if (DebugRecipeUtils.isSlf4jLogger(methodType)) {
                    return mi;
                }

                if (!DebugRecipeUtils.supportsSupplierArguments(methodType)) {
                    return mi;
                }

                List<Expression> args = mi.getArguments();
                if (args.size() < 2) {
                    return mi;
                }

                Expression msg = args.get(0);

                boolean changed = false;
                List<Expression> newArgs = new ArrayList<>(args.size());
                newArgs.add(msg);

                for (int i = 1; i < args.size(); i++) {
                    Expression a = args.get(i);

                    if (DebugRecipeUtils.isAlreadyLazy(a) || DebugRecipeUtils.isTrivial(a)) {
                        newArgs.add(a);
                        continue;
                    }

                    Expression wrapped = DebugRecipeUtils.wrapSupplier(this, supplierLambda, a);
                    newArgs.add(wrapped);
                    changed = true;
                }

                if (!changed) {
                    return mi;
                }

                return mi.withArguments(newArgs);
            }
        };
    }
}
