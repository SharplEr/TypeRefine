# TypeRefine

TypeRefine is a lightweight Java library with an annotation processor for enforcing custom
type-use invariants at compile time.

## What It Does

TypeRefine lets you define domain-specific annotations and use them as type-use markers.

The annotation processor then checks that:

- method arguments match the invariant annotations declared by the target method,
- writes into annotated arrays preserve the array element invariant.

That means mistakes such as swapped arguments of the same java type fail during compilation.

## Maven

```xml
<dependency>
  <groupId>io.github.sharpler</groupId>
  <artifactId>type-refine</artifactId>
  <version>0.1</version>
</dependency>
```

If your build isolates annotation processors from the regular compile classpath,
add `type-refine` to the annotation processor path as well.

## Gradle

Groovy DSL:

```groovy
dependencies {
    compileOnly "io.github.sharpler:type-refine:0.1"
    annotationProcessor "io.github.sharpler:type-refine:0.1"
}
```

Kotlin DSL:

```kotlin
dependencies {
    compileOnly("io.github.sharpler:type-refine:0.1")
    annotationProcessor("io.github.sharpler:type-refine:0.1")
}
```

Use `testCompileOnly` and `testAnnotationProcessor` as well if you declare
invariant annotations inside test sources.

## Example

```java
package demo;

import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import io.github.sharpler.typerefine.annotations.Invariant;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Invariant
@Target(TYPE_USE)
@Retention(CLASS)
@interface ArenaIndex {
}

@Invariant
@Target(TYPE_USE)
@Retention(CLASS)
@interface DocId {
}

final class Arena {
  private final int @ArenaIndex [] arenaIndicesBuffer = new int[8];
  private final int @DocId [] docIdsBuffer = new int[8];

  void fill(@ArenaIndex int arenaIndex, @DocId int docId) {
    arenaIndicesBuffer[0] = arenaIndex;
    docIdsBuffer[0] = docId;
  }
}
```

This compiles:

```java
@ArenaIndex int arenaIndex = 1;
@DocId int docId = 2;
arena.fill(arenaIndex, docId);
```

This does not:

```java
@ArenaIndex int arenaIndex = 1;
@ArenaIndex int docId = 2;
arena.fill(arenaIndex, docId);
```
