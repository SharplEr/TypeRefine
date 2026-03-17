package org.sharpler.typerefine.processor;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.jspecify.annotations.Nullable;

final class InvocationScanner extends TreePathScanner<@Nullable Void, @Nullable Void> {
    private final Trees trees;
    private final Set<String> invariantAnnotationNames;
    private final ProcessingEnvironment processingEnv;

    InvocationScanner(Trees trees, Set<String> invariantAnnotationNames, ProcessingEnvironment processingEnv) {
        this.trees = trees;
        this.invariantAnnotationNames = invariantAnnotationNames;
        this.processingEnv = processingEnv;
    }

    @Override
    public @Nullable Void visitMethodInvocation(MethodInvocationTree node, @Nullable Void unused) {
        var targetElement = trees.getElement(getCurrentPath());
        if (targetElement instanceof ExecutableElement executableElement) {
            verifyInvocation(node, executableElement);
        }
        return super.visitMethodInvocation(node, unused);
    }

    @Override
    public @Nullable Void visitAssignment(AssignmentTree node, @Nullable Void unused) {
        verifyAssignment(node);
        return super.visitAssignment(node, unused);
    }

    private void verifyInvocation(MethodInvocationTree invocation, ExecutableElement executableElement) {
        var parameters = executableElement.getParameters();
        var arguments = invocation.getArguments();
        var checkedArgumentCount = Math.min(parameters.size(), arguments.size());

        for (var index = 0; index < checkedArgumentCount; index++) {
            var parameter = parameters.get(index);
            var expectedInvariant = singleInvariantName(parameter.asType());
            if (expectedInvariant == null) {
                continue;
            }

            var argumentTree = arguments.get(index);
            var argumentPath = new TreePath(getCurrentPath(), argumentTree);
            var actualInvariant = invariantNameForArgument(argumentPath);
            if (expectedInvariant.equals(actualInvariant)) {
                continue;
            }

            var message =
                "Argument %d of %s must carry %s but expression '%s' has %s."
                    .formatted(
                        index + 1,
                        executableElement.getSimpleName(),
                        renderAnnotationName(expectedInvariant),
                        argumentTree,
                        renderActualInvariant(actualInvariant));
            reportMismatch(argumentPath, executableElement, message);
        }
    }

    private void verifyAssignment(AssignmentTree assignment) {
        var expectedInvariant = expectedInvariantForAssignmentTarget(assignment);
        if (expectedInvariant == null) {
            return;
        }

        var expression = assignment.getExpression();
        var expressionPath = new TreePath(getCurrentPath(), expression);
        var actualInvariant = invariantNameForArgument(expressionPath);
        if (expectedInvariant.equals(actualInvariant)) {
            return;
        }

        var executableElement = enclosingExecutableElement(getCurrentPath());

        var message =
            "Assignment in %s must store %s but expression '%s' has %s."
                .formatted(
                    executableElement.getSimpleName(),
                    renderAnnotationName(expectedInvariant),
                    expression,
                    renderActualInvariant(actualInvariant));
        reportMismatch(expressionPath, executableElement, message);
    }

    private void reportMismatch(TreePath argumentPath, ExecutableElement executableElement, String message) {
        var errorElement = diagnosticElement(argumentPath, executableElement);
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, errorElement);
    }

    private ExecutableElement enclosingExecutableElement(TreePath path) {
        for (var currentPath = path; currentPath != null; currentPath = currentPath.getParentPath()) {
            var element = trees.getElement(currentPath);
            if (element instanceof ExecutableElement executableElement) {
                return executableElement;
            }
        }
        throw new IllegalStateException("Expected assignment to be enclosed by an executable element");
    }

    private @Nullable String expectedInvariantForAssignmentTarget(AssignmentTree assignment) {
        var variable = assignment.getVariable();
        if (variable instanceof ArrayAccessTree arrayAccessTree) {
            var arrayPath = new TreePath(getCurrentPath(), arrayAccessTree.getExpression());
            return invariantNameForArgument(arrayPath);
        }

        var variablePath = new TreePath(getCurrentPath(), variable);
        return singleInvariantName(trees.getTypeMirror(variablePath));
    }

    private Element diagnosticElement(TreePath argumentPath, ExecutableElement executableElement) {
        var argumentElement = trees.getElement(argumentPath);
        return argumentElement == null ? executableElement : argumentElement;
    }

    private @Nullable String invariantNameForArgument(TreePath argumentPath) {
        var typeMirror = trees.getTypeMirror(argumentPath);
        var invariantFromType = singleInvariantName(typeMirror);
        if (invariantFromType != null) {
            return invariantFromType;
        }

        var element = trees.getElement(argumentPath);
        if (element == null) {
            return null;
        }

        var invariantFromElementType = singleInvariantName(element.asType());
        if (invariantFromElementType != null) {
            return invariantFromElementType;
        }

        if (element instanceof ExecutableElement executableElement) {
            return singleInvariantName(executableElement.getReturnType());
        }
        return singleInvariantName(element.getAnnotationMirrors());
    }

    private @Nullable String singleInvariantName(@Nullable TypeMirror typeMirror) {
        if (typeMirror == null) {
            return null;
        }
        return singleInvariantName(typeMirror.getAnnotationMirrors());
    }

    private @Nullable String singleInvariantName(List<? extends AnnotationMirror> annotationMirrors) {
        var invariantNames = new ArrayList<String>(1);
        for (var annotationMirror : annotationMirrors) {
            var annotationType = (TypeElement) annotationMirror.getAnnotationType().asElement();
            var qualifiedName = annotationType.getQualifiedName().toString();
            if (invariantAnnotationNames.contains(qualifiedName)) {
                invariantNames.add(qualifiedName);
            }
        }
        if (invariantNames.isEmpty()) {
            return null;
        }
        if (invariantNames.size() == 1) {
            return invariantNames.getFirst();
        }

        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "TypeRefine supports at most one invariant annotation per type use, found: "
                    + invariantNames);
        return null;
    }

    private static String renderActualInvariant(@Nullable String actualInvariant) {
        return actualInvariant == null ? "no invariant annotation" : renderAnnotationName(actualInvariant);
    }

    private static String renderAnnotationName(String qualifiedName) {
        var separatorIndex = qualifiedName.lastIndexOf('.');
        return separatorIndex >= 0 ? "@" + qualifiedName.substring(separatorIndex + 1) : "@" + qualifiedName;
    }
}
