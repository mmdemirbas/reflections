package org.reflections;

import org.jetbrains.annotations.NotNull;
import org.reflections.TestModel.*;

import java.lang.annotation.Annotation;

/**
 * @author Muhammed Demirbaş
 * @since 2018-08-18 04:15
 */
public final class JavaSpecific {

    @NotNull
    public static AF1 newAF1(@NotNull String value) {
        return new AF1() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return AF1.class;
            }

            @Override
            public String value() {
                return value;
            }
        };
    }

    static AC2 newAC2(String value) {
        return new AC2() {
            @Override
            public String value() {return value;}

            @Override
            public Class<? extends Annotation> annotationType() {return AC2.class;}
        };
    }

    static AM1 newAM1(String value) {
        return new AM1() {
            @Override
            public String value() {return value;}

            @Override
            public Class<? extends Annotation> annotationType() {return AM1.class;}
        };
    }
}
