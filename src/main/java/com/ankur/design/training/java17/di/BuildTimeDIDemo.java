package com.ankur.design.training.java17.di;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Build-Time Dependency Injection — full pipeline demo.
 *
 * Pipeline (mirrors how Micronaut/Quarkus work):
 *
 *   javac (compile)
 *      ↓
 *   ClassFileScanner  ← reads .class bytes using java.lang.classfile API
 *      ↓  finds @Component classes + @Inject fields
 *   ContainerCodeGen  ← generates wiring code as a String (real tools write .java files)
 *      ↓
 *   GeneratedContainer (shown as text block below)
 *      ↓
 *   Zero-reflection runtime
 *
 * To run this, the sample classes must already be compiled.
 * Gradle compiles them to build/classes/java/main before running main().
 */
public class BuildTimeDIDemo {

    public static void main(String[] args) throws Exception {

        // ── Step 1: locate compiled classes ──────────────────────────────────
        // In a Gradle plugin this would be project.buildDir/classes/java/main
        // Here we infer it from where this class was loaded from.
        Path buildRoot = resolveBuildRoot();
        Path scanRoot  = buildRoot.resolve(
                "com/ankur/design/training/java17/di/sample"
        );

        System.out.println("Scanning: " + scanRoot);
        System.out.println();

        // ── Step 2: scan .class files ─────────────────────────────────────────
        ClassFileScanner scanner    = new ClassFileScanner();
        List<ClassFileScanner.ComponentModel> components = scanner.scan(scanRoot);

        System.out.println("=== Discovered @Component classes ===");
        for (var c : components) {
            System.out.println("  " + shortName(c.className()));
            for (var ip : c.injectPoints()) {
                System.out.printf("    @Inject %-30s → %s%n",
                        ip.fieldName(), shortName(ip.fieldType()));
            }
        }

        // ── Step 3: show what gets generated ─────────────────────────────────
        System.out.println();
        System.out.println("=== Generated Container Code ===");
        System.out.println(generateContainer(components));

        // ── Step 4: explain the key concepts ─────────────────────────────────
        System.out.println();
        System.out.println("=== Key Concepts ===");
        printConcepts();
    }

    // ── Code generation (in a real tool this writes a .java file) ────────────

    /**
     * Given the scan results, generates a simple container class as a String.
     * A real build plugin would write this to src/generated/java and compile it.
     * No reflection — just plain new() calls.
     */
    private static String generateContainer(List<ClassFileScanner.ComponentModel> components) {

        // Build a lookup: simple class name → fully qualified name
        Map<String, String> typeIndex = components.stream()
                .collect(Collectors.toMap(
                        c -> shortName(c.className()),
                        ClassFileScanner.ComponentModel::className
                ));

        StringBuilder sb = new StringBuilder();
        sb.append("""
                // AUTO-GENERATED — do not edit. Produced by ClassFileScanner at build time.
                // No reflection. No runtime annotation scanning. Pure new() calls.

                public class GeneratedContainer {

                """);

        // one factory method per @Component
        for (var comp : components) {
            String simpleName = shortName(comp.className());
            sb.append("    public static ").append(simpleName)
              .append(" create").append(simpleName).append("() {\n");

            if (comp.injectPoints().isEmpty()) {
                // leaf component — no dependencies
                sb.append("        return new ").append(simpleName).append("();\n");
            } else {
                // wire each @Inject field
                sb.append("        ").append(simpleName)
                  .append(" instance = new ").append(simpleName).append("();\n");

                for (var ip : comp.injectPoints()) {
                    String depSimple = shortName(ip.fieldType());
                    if (typeIndex.containsKey(depSimple)) {
                        // dependency is also a @Component — call its factory
                        sb.append("        // @Inject ").append(ip.fieldName()).append("\n");
                        sb.append("        setField(instance, \"").append(ip.fieldName())
                          .append("\", create").append(depSimple).append("());\n");
                    }
                }
                sb.append("        return instance;\n");
            }
            sb.append("    }\n\n");
        }

        sb.append("""
                    // In generated code this would use MethodHandles.lookup() once at startup,
                    // not Class.forName() + getDeclaredField() on every call.
                    private static void setField(Object target, String name, Object value) {
                        try {
                            var f = target.getClass().getDeclaredField(name);
                            f.setAccessible(true);
                            f.set(target, value);
                        } catch (Exception e) { throw new RuntimeException(e); }
                    }
                }
                """);

        return sb.toString();
    }

    // ── Concepts ─────────────────────────────────────────────────────────────

    private static void printConcepts() {
        System.out.println("""
                1. ClassFile API (java.lang.classfile — Java 24+)
                   ClassFile.of().parse(bytes) → ClassModel
                   No class loading. No JVM warmup. Reads raw bytes from disk.

                2. Annotation retention matters
                   @Retention(CLASS)   → in .class file, stripped at runtime
                                         → ClassFile API can see it, reflection CANNOT
                   @Retention(RUNTIME) → in .class file AND kept at runtime
                                         → both ClassFile API and reflection can see it
                   Build-time tools use CLASS retention → zero runtime overhead.

                3. What ClassFile API sees vs reflection
                   ┌─────────────────────┬─────────────────┬───────────────┐
                   │                     │ ClassFile API   │  Reflection   │
                   ├─────────────────────┼─────────────────┼───────────────┤
                   │ CLASS retention ann │ ✓               │ ✗             │
                   │ RUNTIME retention   │ ✓               │ ✓             │
                   │ Before class load   │ ✓               │ ✗             │
                   │ No JVM side effects │ ✓               │ ✗             │
                   │ Works in build tool │ ✓               │ ✗ (needs JVM) │
                   └─────────────────────┴─────────────────┴───────────────┘

                4. Real frameworks using this pattern
                   Micronaut  → annotation processor (APT) scans at compile time
                   Quarkus     → Jandex index + Arc processor
                   Dagger 2    → APT generates factory classes
                   Spring AOT  → Spring 6 generates hints for native image
                   All of them → no reflection at runtime, fast startup, GraalVM-friendly.

                5. ClassFile API vs ASM
                   ASM  → third-party, visitor pattern, verbose, error-prone
                   ClassFile API → standard JDK, builder pattern, type-safe, sealed hierarchy
                """);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String shortName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static Path resolveBuildRoot() {
        // Walk up from the location of this .class file to find build/classes/java/main
        String classResource = BuildTimeDIDemo.class.getName().replace('.', '/') + ".class";
        String url = BuildTimeDIDemo.class.getClassLoader()
                .getResource(classResource).getPath();
        // url ends with "...build/classes/java/main/com/ankur/.../BuildTimeDIDemo.class"
        Path classPath = Path.of(url);
        // getParent() removes the filename, so we're already at the package leaf dir.
        // We need to go up once per package segment (not counting the filename).
        // e.g. "com/ankur/.../di/BuildTimeDIDemo.class" → 6 package segments → go up 6 times.
        Path current = classPath.getParent(); // start at leaf package dir (di/)
        String[] segments = classResource.split("/");
        int packageDepth = segments.length - 1; // minus the filename
        for (int i = 0; i < packageDepth; i++) current = current.getParent();
        return current; // build/classes/java/main
    }
}