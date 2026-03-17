package io.github.sharpler.typerefine.processor;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
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

/// Walks Java syntax trees and checks invariant consistency on concrete operations.
final class InvocationScanner extends TreePathScanner<@Nullable Void, @Nullable Void> {
    /// Compiler tree utilities for resolving paths, elements, and types.
    private final Trees trees;

    /// Fully qualified names of user annotations marked with `@Invariant`.
    private final Set<String> invariantAnnotationNames;

    /// Processing environment used for emitting diagnostics.
    private final ProcessingEnvironment processingEnv;

    /// Creates a scanner for one processing round.
    InvocationScanner(Trees trees, Set<String> invariantAnnotationNames, ProcessingEnvironment processingEnv) {
        this.trees = trees;
        this.invariantAnnotationNames = invariantAnnotationNames;
        this.processingEnv = processingEnv;
    }

    /// Validates explicit method invocations encountered during tree scanning.
    @Override
    public @Nullable Void visitMethodInvocation(MethodInvocationTree node, @Nullable Void unused) {
        var targetElement = trees.getElement(getCurrentPath());
        if (targetElement instanceof ExecutableElement executableElement) {
            verifyInvocation(node, executableElement);
        }
        return super.visitMethodInvocation(node, unused);
    }

    /// Validates assignments, including writes into annotated array slots.
    @Override
    public @Nullable Void visitAssignment(AssignmentTree node, @Nullable Void unused) {
        verifyAssignment(node);
        return super.visitAssignment(node, unused);
    }

    /// Checks whether each invariant-annotated parameter receives a matching argument.
    private void verifyInvocation(MethodInvocationTree invocation, ExecutableElement executableElement) {
        var parameters = executableElement.getParameters();
        var arguments = invocation.getArguments();
        var checkedArgumentCount = Math.min(parameters.size(), arguments.size());

        for (var i = 0; i < checkedArgumentCount; i++) {
            var parameter = parameters.get(i);
            var expectedInvariant = singleInvariantName(parameter.asType());
            if (expectedInvariant == null) {
                continue;
            }

            var argumentTree = arguments.get(i);
            var argumentPath = new TreePath(getCurrentPath(), argumentTree);
            var actualInvariant = invariantNameForArgument(argumentPath);
            if (expectedInvariant.equals(actualInvariant)) {
                continue;
            }

            var message = "Argument %d of %s must carry %s but expression '%s' has %s."
                .formatted(
                    i + 1,
                    executableElement.getSimpleName(),
                    renderAnnotationName(expectedInvariant),
                    argumentTree,
                    renderActualInvariant(actualInvariant)
                );
            reportMismatch(argumentPath, executableElement, message);
        }
    }

    /// Checks whether an assignment target expects an invariant-annotated value.
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

        var message = "Assignment in %s must store %s but expression '%s' has %s."
            .formatted(
                executableElement.getSimpleName(),
                renderAnnotationName(expectedInvariant),
                expression,
                renderActualInvariant(actualInvariant)
            );

        reportMismatch(expressionPath, executableElement, message);
    }

    /// Emits a compiler error at the most specific element available for the mismatch.
    private void reportMismatch(TreePath argumentPath, ExecutableElement executableElement, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, diagnosticElement(argumentPath, executableElement));
    }

    /// Finds the nearest enclosing method or constructor for the current tree path.
    private ExecutableElement enclosingExecutableElement(TreePath path) {
        for (var currentPath = path; currentPath != null; currentPath = currentPath.getParentPath()) {
            var element = trees.getElement(currentPath);
            if (element instanceof ExecutableElement executableElement) {
                return executableElement;
            }
        }
        throw new IllegalStateException("Expected assignment to be enclosed by an executable element");
    }

    /// Determines the invariant expected by an assignment target.
    ///
    /// For array writes, the invariant is taken from the array type rather than from
    /// `array[index]`, because `javac` stores the annotation on the array type itself.
    private @Nullable String expectedInvariantForAssignmentTarget(AssignmentTree assignment) {
        var variable = assignment.getVariable();
        if (variable instanceof ArrayAccessTree arrayAccessTree) {
            var arrayPath = new TreePath(getCurrentPath(), arrayAccessTree.getExpression());
            return invariantNameForArgument(arrayPath);
        }

        var variablePath = new TreePath(getCurrentPath(), variable);
        return singleInvariantName(trees.getTypeMirror(variablePath));
    }

    /// Chooses the element to which a diagnostic should be attached.
    private Element diagnosticElement(TreePath argumentPath, ExecutableElement executableElement) {
        var argumentElement = trees.getElement(argumentPath);
        return argumentElement == null ? executableElement : argumentElement;
    }

    /// Extracts the single invariant annotation visible on an expression, if any.
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

    /// Extracts the single invariant annotation visible on a type mirror, if any.
    private @Nullable String singleInvariantName(@Nullable TypeMirror typeMirror) {
        if (typeMirror == null) {
            return null;
        }
        return singleInvariantName(typeMirror.getAnnotationMirrors());
    }

    /// Extracts the single invariant annotation from a list of annotation mirrors.
    ///
    /// If more than one invariant annotation is present, a compilation error is emitted.
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
                "TypeRefine supports at most one invariant annotation per type use, found: " + invariantNames
            );
        return null;
    }

    /// Renders the actual invariant name used in diagnostics.
    private static String renderActualInvariant(@Nullable String actualInvariant) {
        return actualInvariant == null ? "no invariant annotation" : renderAnnotationName(actualInvariant);
    }

    /// Renders a fully qualified annotation name as a short `@SimpleName` label.
    private static String renderAnnotationName(String qualifiedName) {
        var separatorIndex = qualifiedName.lastIndexOf('.');
        return separatorIndex >= 0 ? '@' + qualifiedName.substring(separatorIndex + 1) : '@' + qualifiedName;
    }
}
