package org.sharpler.typerefine.processor;

import com.sun.source.util.Trees;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import org.jspecify.annotations.Nullable;
import org.sharpler.typerefine.annotations.Invariant;

@SupportedAnnotationTypes("*")
public final class InvariantProcessor extends AbstractProcessor {
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private @Nullable Trees trees;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        trees = Trees.instance(jbUnwrap(ProcessingEnvironment.class, processingEnv));
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var invariantAnnotationNames = collectInvariantAnnotations(roundEnv);
        var currentTrees = trees;
        if (currentTrees == null || invariantAnnotationNames.isEmpty()) {
            return false;
        }

        for (var rootElement : roundEnv.getRootElements()) {
            var path = currentTrees.getPath(rootElement);
            if (path == null) {
                continue;
            }
            new InvocationScanner(currentTrees, invariantAnnotationNames, processingEnv).scan(path, null);
        }
        return false;
    }

    private static Set<String> collectInvariantAnnotations(RoundEnvironment roundEnv) {
        var result = new HashSet<String>();

        for (var element : roundEnv.getElementsAnnotatedWith(Invariant.class)) {
            if (element.getKind() != ElementKind.ANNOTATION_TYPE) {
                continue;
            }
            var annotationType = (TypeElement) element;
            result.add(annotationType.getQualifiedName().toString());
        }

        return result;
    }

    private static <T> T jbUnwrap(Class<? extends T> iface, T wrapper) {
        try {
            var apiWrappers = wrapper.getClass().getClassLoader().loadClass("org.jetbrains.jps.javac.APIWrappers");
            Method unwrapMethod = apiWrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
            var unwrapped = iface.cast(unwrapMethod.invoke(null, iface, wrapper));
            return unwrapped == null ? wrapper : unwrapped;
        } catch (ReflectiveOperationException ignored) {
            return wrapper;
        }
    }
}
