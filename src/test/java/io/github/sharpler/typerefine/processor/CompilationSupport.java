package io.github.sharpler.typerefine.processor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Assertions;

/// Provides helper methods for compiling in-memory test projects.
final class CompilationSupport {

  /// Prevents instantiation of this utility class.
  private CompilationSupport() {
  }

  /// Compiles a synthetic project with the current `InvariantProcessor`.
  ///
  /// Source files are materialized under `tempDir` before invoking `javac`.
  static CompilationResult compile(Map<String, String> sources, Path tempDir) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    Assertions.assertNotNull(compiler, "System Java compiler is required for compilation tests");

    var sourcesDir = Files.createDirectories(tempDir.resolve("sources"));
    var outputDir = Files.createDirectories(tempDir.resolve("classes"));
    var sourceFiles = new ArrayList<Path>(sources.size());
    for (var entry : sources.entrySet()) {
      var sourcePath = sourcesDir.resolve(entry.getKey());
      Files.createDirectories(sourcePath.getParent());
      Files.writeString(sourcePath, entry.getValue());
      sourceFiles.add(sourcePath);
    }

    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var compilationUnits =
          fileManager.getJavaFileObjectsFromPaths(sourceFiles);
      var options =
          List.of(
              "--class-path", System.getProperty("java.class.path"),
              "-processor", InvariantProcessor.class.getName(),
              "-d", outputDir.toString()
          );
      var task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
      var success = Boolean.TRUE.equals(task.call());
      var renderedDiagnostics =
          diagnostics.getDiagnostics().stream()
              .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(null))
              .toList();
      return new CompilationResult(success, renderedDiagnostics);
    }
  }
}
