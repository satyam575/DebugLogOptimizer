package org.debugBetter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class DebugToFluentRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Convert debug logs to fluent API";
    }

    @Override
    public String getDescription() {
        return "Rewrites SLF4J debug() calls to the fluent API when atDebug() is available.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                mi = super.visitMethodInvocation(mi, ctx);

                if (!"debug".equals(mi.getSimpleName())) {
                    return mi;
                }

                Expression select = mi.getSelect();
                if (select == null) {
                    return mi;
                }

                JavaType.Method methodType = mi.getMethodType();
                if (methodType == null) {
                    if (!DebugRecipeUtils.isLombokSlf4jAnnotated(this)
                            || !DebugRecipeUtils.isLombokSlf4jLoggerSelect(select)) {
                        return mi;
                    }
                } else {
                    if (!DebugRecipeUtils.isSlf4jLogger(methodType)) {
                        return mi;
                    }

                    JavaType.FullyQualified declaring = methodType.getDeclaringType();
                    if (declaring == null || !DebugRecipeUtils.hasMethod(declaring, "atDebug")) {
                        return mi;
                    }
                }

                List<Expression> args = mi.getArguments();
                if (args.isEmpty()) {
                    return mi;
                }

                Expression message = args.get(0);
                JavaType messageType = message.getType();
                if (messageType != null
                        && TypeUtils.isWellFormedType(messageType)
                        && !TypeUtils.isString(messageType)) {
                    return mi;
                }

                JavaTemplate template = JavaTemplate.builder(buildTemplate(args.size() - 1)).build();
                List<Object> params = new ArrayList<>(args.size() + 1);
                params.add(select);
                params.add(message);
                for (int i = 1; i < args.size(); i++) {
                    params.add(args.get(i));
                }

                return template.apply(
                        getCursor(),
                        mi.getCoordinates().replace(),
                        params.toArray()
                );
            }
        };
    }

    private String buildTemplate(int argumentCount) {
        StringBuilder template = new StringBuilder(
                "#{any(java.lang.Object)}.atDebug().setMessage(#{any(java.lang.String)})"
        );
        for (int i = 0; i < argumentCount; i++) {
            template.append(".addArgument(#{any(java.lang.Object)})");
        }
        template.append(".log()");
        return template.toString();
    }
}
