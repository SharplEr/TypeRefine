package io.github.sharpler.typerefine.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/// Verifies the compile-time behavior of `InvariantProcessor`.
final class InvariantProcessorTest {

    /// Verifies that a correctly annotated method call compiles successfully.
    @Test
    void acceptsMatchingInvariantArguments() throws IOException {
        var result = CompilationSupport.compile(validSources(), freshTempDir());

        assertTrue(result.success(), () -> String.join("\n", result.diagnostics()));
    }

    /// Verifies that missing invariant annotations are reported as compilation errors.
    @Test
    void rejectsPlainArgumentsForInvariantParameters() throws IOException {
        var result = CompilationSupport.compile(
            Map.of(
                "demo/ArenaIndex.java", arenaIndexAnnotationSource(),
                "demo/DocId.java", docIdAnnotationSource(),
                "demo/Demo.java", plainArgumentUsageSource()
            ),
            freshTempDir()
        );

        assertFalse(result.success(), "Compilation should fail for unannotated arguments");
        assertTrue(
            result.diagnostics().stream().anyMatch(message -> message.contains("@ArenaIndex")),
            () -> String.join("\n", result.diagnostics())
        );
    }

    /// Verifies that swapping invariant-annotated arguments is rejected.
    @Property
    void rejectsSwappedInvariantArguments(@ForAll boolean swapArguments) throws IOException {
        var result = CompilationSupport.compile(
            Map.of(
                "demo/ArenaIndex.java", arenaIndexAnnotationSource(),
                "demo/DocId.java", docIdAnnotationSource(),
                "demo/Demo.java", swappedArgumentUsageSource(swapArguments)
            ),
            freshTempDir()
        );

        assertTrue(
            result.success() != swapArguments,
            () -> "swapArguments=%s%n%s".formatted(swapArguments, String.join("\n", result.diagnostics())));
    }

    /// Verifies that writes into annotated arrays must respect the array invariant.
    @Test
    void rejectsMismatchedAssignmentsIntoAnnotatedArrays() throws IOException {
        var result =
            CompilationSupport.compile(
                Map.of(
                    "demo/ArenaIndex.java", arenaIndexAnnotationSource(),
                    "demo/DocId.java", docIdAnnotationSource(),
                    "demo/Demo.java", annotatedArrayAssignmentSource()
                ),
                freshTempDir()
            );

        assertFalse(result.success(), "Compilation should fail for mismatched annotated array assignments");
        assertTrue(
            result.diagnostics().stream().anyMatch(message -> message.contains("Assignment in fill")),
            () -> String.join("\n", result.diagnostics())
        );
        assertTrue(
            result.diagnostics().stream().anyMatch(message -> message.contains("@DocId")),
            () -> String.join("\n", result.diagnostics())
        );
    }

    /// Returns a minimal valid multi-file source set for argument checking.
    private static Map<String, String> validSources() {
        return Map.of(
            "demo/ArenaIndex.java", arenaIndexAnnotationSource(),
            "demo/DocId.java", docIdAnnotationSource(),
            "demo/Demo.java", matchingUsageSource()
        );
    }

    /// Returns an invariant annotation source for arena indices.
    private static String arenaIndexAnnotationSource() {
        return """
            package demo;
            
            import static java.lang.annotation.ElementType.TYPE_USE;
            import static java.lang.annotation.RetentionPolicy.CLASS;
            
            import java.lang.annotation.Retention;
            import java.lang.annotation.Target;
            import io.github.sharpler.typerefine.annotations.Invariant;
            
            @Invariant
            @Target(TYPE_USE)
            @Retention(CLASS)
            public @interface ArenaIndex {
            }
            """;
    }

    /// Returns an invariant annotation source for document identifiers.
    private static String docIdAnnotationSource() {
        return """
            package demo;
            
            import static java.lang.annotation.ElementType.TYPE_USE;
            import static java.lang.annotation.RetentionPolicy.CLASS;
            
            import java.lang.annotation.Retention;
            import java.lang.annotation.Target;
            import io.github.sharpler.typerefine.annotations.Invariant;
            
            @Invariant
            @Target(TYPE_USE)
            @Retention(CLASS)
            public @interface DocId {
            }
            """;
    }

    /// Returns a source snippet with a valid invariant-preserving method call.
    private static String matchingUsageSource() {
        return """
            package demo;
            
            final class Demo {
              static void fillDoc(@ArenaIndex int index, @DocId int docId) {
              }
            
              void compile() {
                @ArenaIndex int index = 1;
                @DocId int docId = 2;
                fillDoc(index, docId);
              }
            }
            """;
    }

    /// Returns a source snippet with a missing invariant annotation on one argument.
    private static String plainArgumentUsageSource() {
        return """
            package demo;
            
            final class Demo {
              static void fillDoc(@ArenaIndex int index, @DocId int docId) {
              }
            
              void compile() {
                var index = 1;
                @DocId int docId = 2;
                fillDoc(index, docId);
              }
            }
            """;
    }

    /// Returns a source snippet whose invocation order depends on `swapArguments`.
    private static String swappedArgumentUsageSource(boolean swapArguments) {
        var invocation = swapArguments ? "fillDoc(docId, index);" : "fillDoc(index, docId);";
        return """
            package demo;
            
            final class Demo {
              static void fillDoc(@ArenaIndex int index, @DocId int docId) {
              }
            
              void compile() {
                @ArenaIndex int index = 1;
                @DocId int docId = 2;
                %s
              }
            }
            """.formatted(invocation);
    }

    /// Returns a source snippet that writes an `@ArenaIndex` value into an `@DocId` array.
    private static String annotatedArrayAssignmentSource() {
        return """
            package demo;
            
            final class Demo {
              private final int @ArenaIndex [] arenaIndicesBuffer = new int[8];
              private final int @DocId [] docIdsBuffer = new int[8];
            
              void fill(@ArenaIndex int arenaIndex, @ArenaIndex int docId) {
                arenaIndicesBuffer[0] = arenaIndex;
                docIdsBuffer[0] = docId;
              }
            }
            """;
    }

    /// Creates an isolated temporary directory for one compilation test.
    private static Path freshTempDir() throws IOException {
        return Files.createTempDirectory("typerefine-test-");
    }
}
