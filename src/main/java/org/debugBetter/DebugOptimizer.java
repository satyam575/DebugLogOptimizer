package org.debugBetter;


import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
public class DebugOptimizer extends Recipe {

        @Override
        public String getDisplayName() {
            return "Lazify debug log arguments with Supplier";
        }

        @Override
        public String getDescription() {
            return "Wraps non-trivial debug() arguments in () -> expr to avoid eager evaluation when debug is disabled.";
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

                    List<Expression> args = mi.getArguments();
                    if (args.size() < 2) {
                        return mi;
                    }

                    // Keep the message/template arg as-is.
                    Expression msg = args.get(0);

                    boolean changed = false;
                    List<Expression> newArgs = new ArrayList<>(args.size());
                    newArgs.add(msg);

                    for (int i = 1; i < args.size(); i++) {
                        Expression a = args.get(i);

                        if (isAlreadyLazy(a) || isTrivial(a)) {
                            newArgs.add(a);
                            continue;
                        }

                        // Wrap: () -> a
                        Expression wrapped = supplierLambda.apply(
                                getCursor(),
                                a.getCoordinates().replace(),
                                a
                        ).withPrefix(a.getPrefix());

                        newArgs.add(wrapped);
                        changed = true;
                    }

                    if (!changed) {
                        return mi;
                    }

                    return mi.withArguments(newArgs);
                }

                private boolean isAlreadyLazy(Expression e) {
                    return e instanceof J.Lambda || e instanceof J.MemberReference;
                }

                /**
                 * “Trivial” means likely cheap and/or already a value:
                 * - identifiers (dto)
                 * - field access (this.dto, foo.bar)
                 * - literals (42, "x", null)
                 *
                 * Everything else we treat as potentially expensive:
                 * - method invocation (dto.getSomething())
                 * - new expressions
                 * - string concatenation, ternary, cast, etc.
                 */
                private boolean isTrivial(Expression e) {
                    if (e instanceof J.Identifier) return true;
                    if (e instanceof J.FieldAccess) return true;
                    if (e instanceof J.Literal) return true;

                    // Optional: treat array access as trivial? depends.
                    // if (e instanceof J.ArrayAccess) return true;

                    return false;
                }
            };
        }


}
