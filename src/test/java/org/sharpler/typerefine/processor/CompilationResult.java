package org.sharpler.typerefine.processor;

import java.util.List;

record CompilationResult(boolean success, List<String> diagnostics) {
}
