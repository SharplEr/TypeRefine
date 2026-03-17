package org.sharpler.typerefine.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/// Marks another annotation as a TypeRefine invariant annotation.
///
/// Any annotation annotated with `@Invariant` becomes eligible for compile-time
/// checks performed by `InvariantProcessor`.
@Documented
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
public @interface Invariant {
}
