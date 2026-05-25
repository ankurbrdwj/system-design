package com.ankur.design.training.java17.di.annotations;

import java.lang.annotation.*;

/**
 * Marks a field for dependency injection.
 * The build-time scanner finds these fields and wires them in the generated container.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Inject {}