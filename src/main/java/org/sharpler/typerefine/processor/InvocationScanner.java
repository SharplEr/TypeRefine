package org.sharpler.typerefine.processor;

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

            reportMismatch(argumentPath, argumentTree, executableElement, index, expectedInvariant, actualInvariant);
        }
    }

    private void reportMismatch(
        TreePath argumentPath,
        ExpressionTree argumentTree,
        ExecutableElement executableElement,
        int parameterIndex,
        String expectedInvariant,
        @Nullable String actualInvariant
    ) {
        var methodName = executableElement.getSimpleName();
        var renderedExpectedInvariant = renderAnnotationName(expectedInvariant);
        var renderedActualInvariant =
            actualInvariant == null ? "no invariant annotation" : renderAnnotationName(actualInvariant);
        var message =
            "Argument %d of %s must carry %s but expression '%s' has %s."
                .formatted(
                    parameterIndex + 1,
                    methodName,
                    renderedExpectedInvariant,
                    argumentTree,
                    renderedActualInvariant);
        var errorElement = diagnosticElement(argumentPath, executableElement);
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, errorElement);
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

    private @Nullable String singleInvariantName(TypeMirror typeMirror) {
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

    private static String renderAnnotationName(String qualifiedName) {
        var separatorIndex = qualifiedName.lastIndexOf('.');
        return separatorIndex >= 0 ? "@" + qualifiedName.substring(separatorIndex + 1) : "@" + qualifiedName;
    }
}
