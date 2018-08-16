package org.reflections;

import com.google.common.base.Function;
import com.google.common.collect.Sets;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Test;
import org.reflections.TestModel.*;
import org.reflections.scanners.FieldAnnotationsScanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

import static com.google.common.collect.Collections2.transform;
import static org.junit.Assert.*;
import static org.reflections.ReflectionUtils.*;
import static org.reflections.ReflectionsTest.are;

/**
 * @author mamo
 */
@SuppressWarnings("unchecked")
public class ReflectionUtilsTest {

    @Test
    public void getAllTest() {
        assertThat(getAllSuperTypes(C3.class, withAnnotation(AI1.class)), are(I1.class));

        Set<Method> allMethods  = getAllMethods(C4.class, withModifier(Modifier.PUBLIC), withReturnType(void.class));
        Set<Method> allMethods1 = getAllMethods(C4.class, withPattern("public.*.void .*"));

        assertTrue(allMethods.containsAll(allMethods1) && allMethods1.containsAll(allMethods));
        assertThat(allMethods1, names("m1"));

        assertThat(getAllMethods(C4.class, withAnyParameterAnnotation(AM1.class)), names("m4"));

        assertThat(getAllFields(C4.class, withAnnotation(AF1.class)), names("f1", "f2"));

        assertThat(getAllFields(C4.class, withAnnotation(new AF1() {
            @Override
            public String value() {return "2";}

            @Override
            public Class<? extends Annotation> annotationType() {return AF1.class;}
        })), names("f2"));

        assertThat(getAllFields(C4.class, withTypeAssignableTo(String.class)), names("f1", "f2", "f3"));

        assertThat(getAllConstructors(C4.class, withParametersCount(0)), names(C4.class.getName()));

        assertEquals(getAllAnnotations(C3.class).size(), 5);

        Method m4 = getMethods(C4.class, withName("m4")).iterator().next();
        assertEquals(m4.getName(), "m4");
        assertTrue(getAnnotations(m4).isEmpty());
    }

    @Test
    public void withParameter() throws Exception {
        Class  target = Collections.class;
        Object arg1   = Arrays.asList(1, 2, 3);

        Set<Method> allMethods = Sets.newHashSet();
        for (Class<?> type : getAllSuperTypes(arg1.getClass())) {
            allMethods.addAll(getAllMethods(target, withModifier(Modifier.STATIC), withParameters(type)));
        }

        Set<Method> allMethods1 = getAllMethods(target,
                                                withModifier(Modifier.STATIC),
                                                withParametersAssignableTo(arg1.getClass()));

        assertEquals(allMethods, allMethods1);

        for (Method method : allMethods) { //effectively invokable
            Object invoke = method.invoke(null, arg1);
        }
    }

    @Test
    public void withParametersAssignableFromTest() throws Exception {
        //Check for null safe
        getAllMethods(Collections.class, withModifier(Modifier.STATIC), withParametersAssignableFrom());

        Class  target = Collections.class;
        Object arg1   = Arrays.asList(1, 2, 3);

        Set<Method> allMethods = Sets.newHashSet();
        for (Class<?> type : getAllSuperTypes(arg1.getClass())) {
            allMethods.addAll(getAllMethods(target, withModifier(Modifier.STATIC), withParameters(type)));
        }

        Set<Method> allMethods1 = getAllMethods(target,
                                                withModifier(Modifier.STATIC),
                                                withParametersAssignableFrom(Iterable.class),
                                                withParametersAssignableTo(arg1.getClass()));

        assertEquals(allMethods, allMethods1);

        for (Method method : allMethods) { //effectively invokable
            Object invoke = method.invoke(null, arg1);
        }
    }

    @Test
    public void withReturn() {
        Set<Method> returnMember              = getAllMethods(Class.class, withReturnTypeAssignableTo(Member.class));
        Set<Method> returnsAssignableToMember = getAllMethods(Class.class, withReturnType(Method.class));

        assertTrue(returnMember.containsAll(returnsAssignableToMember));
        assertFalse(returnsAssignableToMember.containsAll(returnMember));

        returnsAssignableToMember = getAllMethods(Class.class, withReturnType(Field.class));
        assertTrue(returnMember.containsAll(returnsAssignableToMember));
        assertFalse(returnsAssignableToMember.containsAll(returnMember));
    }

    @Test
    public void getAllAndReflections() {
        Reflections reflections = new Reflections(TestModel.class, new FieldAnnotationsScanner());

        Set<Field> af1       = reflections.getFieldsAnnotatedWith(AF1.class);
        Set<Field> allFields = getAll(af1, withModifier(Modifier.PROTECTED));
        assertTrue(allFields.size() == 1);
        assertThat(allFields, names("f2"));
    }

    private Set<String> names(Set<? extends Member> o) {
        return Sets.newHashSet(transform(o, (Function<Member, String>) input -> input.getName()));
    }

    private BaseMatcher<Set<? extends Member>> names(String... namesArray) {
        return new BaseMatcher<Set<? extends Member>>() {

            @Override
            public boolean matches(Object o) {
                Collection<String> transform = names((Set<Member>) o);
                Collection<?>      names     = Arrays.asList(namesArray);
                return transform.containsAll(names) && names.containsAll(transform);
            }

            @Override
            public void describeTo(Description description) {
            }
        };
    }
}
