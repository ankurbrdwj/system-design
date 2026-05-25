package com.ankur.design.training.java17.di.annotations;

import java.lang.annotation.*;

/**
 * Marks a class as a managed component — the DI container will create and wire it.
 * Retention = CLASS: the annotation is in the .class file but stripped at runtime.
 * This is sufficient for build-time scanning with the ClassFile API.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Component {}