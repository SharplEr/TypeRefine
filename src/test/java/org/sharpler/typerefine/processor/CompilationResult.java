package org.sharpler.typerefine.processor;

import java.util.List;

/// Stores the outcome of a compilation test run.
///
/// - `success` tells whether compilation completed without errors.
/// - `diagnostics` stores rendered compiler diagnostics for assertions.
record CompilationResult(boolean success, List<String> diagnostics) {
}
