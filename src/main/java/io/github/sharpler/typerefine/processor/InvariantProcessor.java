package io.github.sharpler.typerefine.processor;

import com.sun.source.util.Trees;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import io.github.sharpler.typerefine.annotations.Invariant;
import org.apiguardian.api.API;
import org.jspecify.annotations.Nullable;

/// Scans compilation units for invariant annotations and validates their usage.
///
/// The processor currently enforces invariant consistency for:
/// - explicit method invocations,
/// - assignments into annotated array slots.
@API(status = API.Status.INTERNAL, since = "0.1")
@SuppressWarnings("WeakerAccess")
@SupportedAnnotationTypes("*")
public final class InvariantProcessor extends AbstractProcessor {
    /// Lazily initialized tree access used to inspect method calls and assignments.
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private @Nullable Trees trees;

    /// Initializes the processor and unwraps JetBrains JPS wrappers when needed.
    ///
    /// `super.init(...)` must still receive the original wrapper because JPS relies on it.
    @API(status = API.Status.INTERNAL, since = "0.1")
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        trees = Trees.instance(jbUnwrap(ProcessingEnvironment.class, processingEnv));
    }

    /// Returns the newest source version supported by the running compiler.
    @API(status = API.Status.INTERNAL, since = "0.1")
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /// Processes the current round and validates all roots that contain invariant usage.
    @API(status = API.Status.INTERNAL, since = "0.1")
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

    /// Collects all annotation type names that are themselves annotated with `@Invariant`.
    private static Set<String> collectInvariantAnnotations(RoundEnvironment roundEnv) {
        var elements = roundEnv.getElementsAnnotatedWith(Invariant.class);
        var result = new HashSet<String>(elements.size());
        for (var element : elements) {
            if (element.getKind() != ElementKind.ANNOTATION_TYPE) {
                continue;
            }
            var annotationType = (TypeElement) element;
            result.add(annotationType.getQualifiedName().toString());
        }

        return result;
    }

    /// Unwraps IntelliJ JPS wrapper objects when the processor needs raw compiler APIs.
    ///
    /// When the wrapper type is not available, the original object is returned unchanged.
    private static <T> T jbUnwrap(Class<? extends T> iface, T wrapper) {
        try {
            var apiWrappers = wrapper.getClass().getClassLoader().loadClass("org.jetbrains.jps.javac.APIWrappers");
            var unwrapMethod = apiWrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
            var unwrapped = iface.cast(unwrapMethod.invoke(null, iface, wrapper));
            return unwrapped == null ? wrapper : unwrapped;
        } catch (ReflectiveOperationException ignored) {
            return wrapper;
        }
    }
}
