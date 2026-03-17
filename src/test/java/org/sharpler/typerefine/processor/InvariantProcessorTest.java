package org.sharpler.typerefine.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

final class InvariantProcessorTest {

  @Test
  void acceptsMatchingInvariantArguments() throws IOException {
    var result = CompilationSupport.compile(validSources(), freshTempDir());

    assertTrue(result.success(), () -> String.join("\n", result.diagnostics()));
  }

  @Test
  void rejectsPlainArgumentsForInvariantParameters() throws IOException {
    var result =
        CompilationSupport.compile(
            Map.of(
                "demo/ArenaIndex.java", arenaIndexAnnotationSource(),
                "demo/DocId.java", docIdAnnotationSource(),
                "demo/Demo.java", plainArgumentUsageSource()),
            freshTempDir());

    assertFalse(result.success(), "Compilation should fail for unannotated arguments");
    assertTrue(
        result.diagnostics().stream().anyMatch(message -> message.contains("@ArenaIndex")),
        () -> String.join("\n", result.diagnostics()));
  }

  @Property
  void rejectsSwappedInvariantArguments(@ForAll boolean swapArguments) throws IOException {
    var result =
        CompilationSupport.compile(
            Map.of(
                "demo/ArenaIndex.java", arenaIndexAnnotationSource(),
                "demo/DocId.java", docIdAnnotationSource(),
                "demo/Demo.java", swappedArgumentUsageSource(swapArguments)),
            freshTempDir());

    assertTrue(
        result.success() != swapArguments,
        () ->
            "swapArguments=%s%n%s"
                .formatted(swapArguments, String.join("\n", result.diagnostics())));
  }

  @Test
  void rejectsMismatchedAssignmentsIntoAnnotatedArrays() throws IOException {
    var result =
        CompilationSupport.compile(
            Map.of(
                "demo/ArenaIndex.java", arenaIndexAnnotationSource(),
                "demo/DocId.java", docIdAnnotationSource(),
                "demo/Demo.java", annotatedArrayAssignmentSource()),
            freshTempDir());

    assertFalse(result.success(), "Compilation should fail for mismatched annotated array assignments");
    assertTrue(
        result.diagnostics().stream().anyMatch(message -> message.contains("Assignment in fill")),
        () -> String.join("\n", result.diagnostics()));
    assertTrue(
        result.diagnostics().stream().anyMatch(message -> message.contains("@DocId")),
        () -> String.join("\n", result.diagnostics()));
  }

  private static Map<String, String> validSources() {
    return Map.of(
        "demo/ArenaIndex.java", arenaIndexAnnotationSource(),
        "demo/DocId.java", docIdAnnotationSource(),
        "demo/Demo.java", matchingUsageSource());
  }

  private static String arenaIndexAnnotationSource() {
    return """
        package demo;

        import static java.lang.annotation.ElementType.TYPE_USE;
        import static java.lang.annotation.RetentionPolicy.CLASS;

        import java.lang.annotation.Retention;
        import java.lang.annotation.Target;
        import org.sharpler.typerefine.annotations.Invariant;

        @Invariant
        @Target(TYPE_USE)
        @Retention(CLASS)
        public @interface ArenaIndex {
        }
        """;
  }

  private static String docIdAnnotationSource() {
    return """
        package demo;

        import static java.lang.annotation.ElementType.TYPE_USE;
        import static java.lang.annotation.RetentionPolicy.CLASS;

        import java.lang.annotation.Retention;
        import java.lang.annotation.Target;
        import org.sharpler.typerefine.annotations.Invariant;

        @Invariant
        @Target(TYPE_USE)
        @Retention(CLASS)
        public @interface DocId {
        }
        """;
  }

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

  private static Path freshTempDir() throws IOException {
    return Files.createTempDirectory("typerefine-test-");
  }
}
