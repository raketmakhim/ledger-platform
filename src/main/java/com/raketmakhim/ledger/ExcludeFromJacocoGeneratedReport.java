package com.raketmakhim.ledger;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Keeps the annotated code out of the JaCoCo coverage report. JaCoCo skips anything
 * annotated with an annotation whose name contains "Generated" and whose retention
 * is CLASS or RUNTIME, so do not rename this. Always say why in a comment next to the use.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface ExcludeFromJacocoGeneratedReport {
}
