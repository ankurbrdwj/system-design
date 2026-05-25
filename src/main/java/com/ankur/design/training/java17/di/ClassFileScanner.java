package com.ankur.design.training.java17.di;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Build-time scanner using the Java ClassFile API (java.lang.classfile, finalized Java 24).
 *
 * What it does at build time:
 *   1. Walks a directory of compiled .class files
 *   2. Parses each file using ClassFile.of().parse(bytes)
 *   3. Looks for @Component on classes  → these are beans the container manages
 *   4. Looks for @Inject on fields      → these are wiring points
 *   5. Builds a ComponentModel (class name + inject fields) for each @Component
 *
 * Why ClassFile API vs reflection?
 *   Reflection requires loading the class into the JVM → triggers static initialisers,
 *   needs all transitive dependencies on the classpath, can't run before the app starts.
 *   ClassFile API reads raw bytes → no class loading, no runtime cost, works in any
 *   build tool (Gradle plugin, Maven plugin, annotation processor alternative).
 *
 * This is exactly how Micronaut and Quarkus (Arc) work:
 *   compile → scan .class files → generate wiring code → no reflection at runtime.
 */
public class ClassFileScanner {

    // Descriptor strings for our custom annotations as they appear in bytecode
    // Format: "L<binary/name>;" — slashes not dots
    private static final String COMPONENT_DESC =
            "Lcom/ankur/design/training/java17/di/annotations/Component;";
    private static final String INJECT_DESC =
            "Lcom/ankur/design/training/java17/di/annotations/Inject;";

    // ── Result model ─────────────────────────────────────────────────────────

    /**
     * Represents one @Component class discovered at build time.
     * Records make this a clean data holder with no boilerplate.
     */
    public record ComponentModel(
            String className,               // e.g. "com.ankur.design.....OrderService"
            List<InjectPoint> injectPoints  // fields annotated with @Inject
    ) {}

    /**
     * One @Inject field inside a component.
     */
    public record InjectPoint(
            String fieldName,   // e.g. "orderRepository"
            String fieldType    // e.g. "com.ankur.design.....OrderRepository"
    ) {}

    // ── Main scan entry point ─────────────────────────────────────────────────

    /**
     * Scans all .class files under rootDir and returns models for @Component classes.
     *
     * @param rootDir  build output directory, e.g. "build/classes/java/main"
     */
    public List<ComponentModel> scan(Path rootDir) throws IOException {
        List<ComponentModel> components = new ArrayList<>();

        Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".class")) {
                    scanClassFile(file).ifPresent(components::add);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return components;
    }

    // ── Parse one .class file ─────────────────────────────────────────────────

    private Optional<ComponentModel> scanClassFile(Path classFile) throws IOException {
        byte[] bytes = Files.readAllBytes(classFile);

        // ClassFile.of() — entry point to the ClassFile API
        // parse(bytes) — returns ClassModel: structured view of the .class file
        ClassModel classModel = ClassFile.of().parse(bytes);

        // Step 1: check if this class has @Component
        if (!hasAnnotation(classModel, COMPONENT_DESC)) {
            return Optional.empty();
        }

        // Convert binary name "com/ankur/.../OrderService" → "com.ankur.....OrderService"
        String className = classModel.thisClass().name().stringValue().replace('/', '.');

        // Step 2: find all fields annotated with @Inject
        List<InjectPoint> injectPoints = new ArrayList<>();

        for (FieldModel field : classModel.fields()) {
            if (hasFieldAnnotation(field, INJECT_DESC)) {
                String fieldName = field.fieldName().stringValue();
                String fieldType = descriptorToClassName(field.fieldTypeSymbol());
                injectPoints.add(new InjectPoint(fieldName, fieldType));
            }
        }

        return Optional.of(new ComponentModel(className, List.copyOf(injectPoints)));
    }

    // ── Annotation checking ───────────────────────────────────────────────────

    /**
     * Checks class-level annotations.
     *
     * Annotations with CLASS retention → RuntimeInvisibleAnnotationsAttribute
     * Annotations with RUNTIME retention → RuntimeVisibleAnnotationsAttribute
     * Our @Component/@Inject use CLASS retention → Invisible attribute.
     */
    private boolean hasAnnotation(ClassModel classModel, String descriptorToFind) {
        // CLASS retention → invisible at runtime, but present in .class file
        var invisible = classModel.findAttribute(Attributes.runtimeInvisibleAnnotations());
        if (invisible.isPresent()) {
            for (Annotation ann : invisible.get().annotations()) {
                if (ann.classSymbol().descriptorString().equals(descriptorToFind)) return true;
            }
        }
        // RUNTIME retention — check this too in case someone changes retention
        var visible = classModel.findAttribute(Attributes.runtimeVisibleAnnotations());
        if (visible.isPresent()) {
            for (Annotation ann : visible.get().annotations()) {
                if (ann.classSymbol().descriptorString().equals(descriptorToFind)) return true;
            }
        }
        return false;
    }

    private boolean hasFieldAnnotation(FieldModel field, String descriptorToFind) {
        var invisible = field.findAttribute(Attributes.runtimeInvisibleAnnotations());
        if (invisible.isPresent()) {
            for (Annotation ann : invisible.get().annotations()) {
                if (ann.classSymbol().descriptorString().equals(descriptorToFind)) return true;
            }
        }
        var visible = field.findAttribute(Attributes.runtimeVisibleAnnotations());
        if (visible.isPresent()) {
            for (Annotation ann : visible.get().annotations()) {
                if (ann.classSymbol().descriptorString().equals(descriptorToFind)) return true;
            }
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * ClassDesc descriptor "Lcom/example/Foo;" → "com.example.Foo"
     */
    private String descriptorToClassName(ClassDesc desc) {
        String d = desc.descriptorString();
        if (d.startsWith("L") && d.endsWith(";")) {
            return d.substring(1, d.length() - 1).replace('/', '.');
        }
        return d; // primitive or array — return as-is
    }
}