package org.debugBetter;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class DebugRecipeUtils {
    private DebugRecipeUtils() {
    }

    static Expression wrapSupplier(
            JavaIsoVisitor<ExecutionContext> visitor,
            JavaTemplate template,
            Expression expression
    ) {
        Cursor argCursor = new Cursor(visitor.getCursor(), expression);
        return template.apply(
                argCursor,
                expression.getCoordinates().replace(),
                expression
        ).withPrefix(expression.getPrefix());
    }

    static Statement applyGuard(
            JavaIsoVisitor<ExecutionContext> visitor,
            JavaTemplate template,
            J.MethodInvocation mi,
            Expression select
    ) {
        return template.apply(
                visitor.getCursor(),
                mi.getCoordinates().replace(),
                select,
                mi
        );
    }

    static boolean isAlreadyLazy(Expression e) {
        return e instanceof J.Lambda || e instanceof J.MemberReference;
    }

    static boolean isTrivial(Expression e) {
        if (e instanceof J.Literal) {
            return true;
        }

        JavaType type = e.getType();
        if (type == null || !TypeUtils.isWellFormedType(type)) {
            return false;
        }

        if (TypeUtils.asPrimitive(type) != null) {
            return true;
        }

        if (TypeUtils.isString(type)) {
            return true;
        }

        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq == null) {
            return false;
        }

        return isBoxedPrimitive(fq.getFullyQualifiedName());
    }

    static boolean isSlf4jLogger(JavaType.Method methodType) {
        JavaType.FullyQualified declaring = methodType.getDeclaringType();
        if (declaring == null) {
            return false;
        }
        return TypeUtils.isAssignableTo("org.slf4j.Logger", declaring);
    }

    static boolean supportsSupplierArguments(JavaType.Method methodType) {
        JavaType.FullyQualified declaring = methodType.getDeclaringType();
        if (declaring == null) {
            return false;
        }

        List<JavaType.Method> methods = declaring.getMethods();
        if (methods == null || methods.isEmpty()) {
            return false;
        }

        for (JavaType.Method m : methods) {
            if (!"debug".equals(m.getName())) {
                continue;
            }
            if (hasSupplierParameter(m)) {
                return true;
            }
        }

        return false;
    }

    static boolean hasMethod(JavaType.FullyQualified declaring, String name) {
        if (declaring == null) {
            return false;
        }

        List<JavaType.Method> methods = declaring.getMethods();
        if (methods == null || methods.isEmpty()) {
            return false;
        }

        for (JavaType.Method m : methods) {
            if (name.equals(m.getName())) {
                return true;
            }
        }

        return false;
    }

    static boolean isSafeLoggerSelect(Expression select) {
        return select instanceof J.Identifier
                || select instanceof J.FieldAccess;
    }

    static boolean isLombokSlf4jAnnotated(JavaIsoVisitor<ExecutionContext> visitor) {
        Cursor cursor = visitor.getCursor();
        Cursor parent = cursor.dropParentUntil(p -> p instanceof J.ClassDeclaration);
        if (!(parent.getValue() instanceof J.ClassDeclaration classDecl)) {
            return false;
        }

        for (J.Annotation annotation : classDecl.getAllAnnotations()) {
            if ("Slf4j".equals(annotation.getSimpleName())) {
                return true;
            }
        }

        return false;
    }

    static boolean isLombokSlf4jLoggerSelect(Expression select) {
        if (select instanceof J.Identifier identifier) {
            return "log".equals(identifier.getSimpleName());
        }
        if (select instanceof J.FieldAccess fieldAccess) {
            return "log".equals(fieldAccess.getSimpleName());
        }
        return false;
    }

    static boolean isAlreadyGuarded(JavaIsoVisitor<ExecutionContext> visitor) {
        Cursor cursor = visitor.getCursor();
        Cursor parent = cursor.dropParentUntil(p -> p instanceof J.If);
        if (!(parent.getValue() instanceof J.If ifStmt)) {
            return false;
        }

        AtomicBoolean hasDebugEnabled = new AtomicBoolean(false);
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, AtomicBoolean found) {
                if ("isDebugEnabled".equals(mi.getSimpleName())) {
                    found.set(true);
                }
                return super.visitMethodInvocation(mi, found);
            }
        }.visit(ifStmt.getIfCondition().getTree(), hasDebugEnabled);

        return hasDebugEnabled.get();
    }

    private static boolean hasSupplierParameter(JavaType.Method method) {
        List<JavaType> paramTypes = method.getParameterTypes();
        if (paramTypes == null || paramTypes.isEmpty()) {
            return false;
        }

        for (JavaType param : paramTypes) {
            if (isSupplierType(param)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isSupplierType(JavaType type) {
        if (type == null) {
            return false;
        }

        JavaType.Array array = TypeUtils.asArray(type);
        if (array != null) {
            return isSupplierType(array.getElemType());
        }

        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq == null) {
            return false;
        }

        String name = fq.getFullyQualifiedName();
        return "java.util.function.Supplier".equals(name)
                || "org.apache.logging.log4j.util.Supplier".equals(name);
    }

    private static boolean isBoxedPrimitive(String fqName) {
        return switch (fqName) {
            case "java.lang.Boolean",
                    "java.lang.Byte",
                    "java.lang.Short",
                    "java.lang.Integer",
                    "java.lang.Long",
                    "java.lang.Float",
                    "java.lang.Double",
                    "java.lang.Character",
                    "java.lang.Void" -> true;
            default -> false;
        };
    }
}
