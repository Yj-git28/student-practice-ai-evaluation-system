package org.example.backend.security;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPerm {

    String[] value();

    Logical logical() default Logical.AND;

    enum Logical { AND, OR }
}
