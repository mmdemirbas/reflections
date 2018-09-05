package com.mmdemirbas.reflections;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@SuppressWarnings("ALL")
public interface TestModel {

    @Retention(RUNTIME)
    @Inherited
    @interface MAI1 {}

    @Retention(RUNTIME)
    @MAI1
    @interface AI1 {}

    @AI1
    interface I1 {}

    @Retention(RUNTIME)
    @Inherited
    @interface AI2 {}

    @AI2
    interface I2 extends I1 {}

    @Retention(RUNTIME)
    @Inherited
    @interface AC1 {}

    @Retention(RUNTIME)
    @interface AC1n {}

    @AC1
    @AC1n
    class C1 implements I2 {}

    @Retention(RUNTIME)
    @interface AC2 {

        String value();
    }

    @AC2("grr...")
    class C2 extends C1 {}

    @AC2("ugh?!")
    class C3 extends C1 {}

    @Retention(RUNTIME)
    @interface AM1 {

        String value();
    }

    @Retention(RUNTIME)
    @interface AF1 {

        String value();
    }

    class C4 {

        @AF1("1") private   String f1;
        @AF1("2") protected String f2;
        protected           String f3;

        public C4() { }

        @AM1("1")
        public C4(@AM1("1") String f1) { this.f1 = f1; }

        @AM1("1")
        protected void m1() {}

        @AM1("1")
        public void m1(int integer, String... strings) {}

        @AM1("1")
        public void m1(int[][] integer, String[][] strings) {}

        @AM1("2")
        public String m3() {return null;}

        public String m4(@AM1("2") String string) {return null;}

        public C3 c2toC3(C2 c2)                   {return null;}

        public int add(int i1, int i2)            { return i1 + i2; }
    }

    class C5 extends C3 {}

    @AC2("ugh?!")
    interface I3 {}

    class C6 implements I3 {}

    @Retention(RUNTIME)
    @AC2("ugh?!")
    @interface AC3 {}

    @AC3
    class C7 {}

    interface Usage {

        class C1 {

            C2 c2 = new C2();

            public C1()                       { }

            public C1(C2 c2)                  { this.c2 = c2; }

            public void method()              { c2.method(); }

            public void method(String string) { c2.method(); }
        }

        class C2 {

            C1 c1 = new C1();

            public void method() {
                c1 = new C1();
                c1 = new C1(this);
                c1.method();
                c1.method("");
            }
        }
    }
}
