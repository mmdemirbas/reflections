package org.reflections.adapters;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.reflections.util.Utils;
import org.reflections.vfs.Vfs.File;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

import static org.reflections.ReflectionUtils.forName;

/** */
public class JavaReflectionAdapter implements MetadataAdapter<Class, Field, Member> {

    @Override
    public List<Field> getFields(Class cls) {
        return Lists.newArrayList(cls.getDeclaredFields());
    }

    @Override
    public List<Member> getMethods(Class cls) {
        List<Member> methods = Lists.newArrayList();
        methods.addAll(Arrays.asList(cls.getDeclaredMethods()));
        methods.addAll(Arrays.asList(cls.getDeclaredConstructors()));
        return methods;
    }

    @Override
    public String getMethodName(Member method) {
        return (method instanceof Method) ? method.getName() : ((method instanceof Constructor) ? "<init>" : null);
    }

    @Override
    public List<String> getParameterNames(Member member) {
        List<String> result = Lists.newArrayList();

        Class<?>[] parameterTypes = (member instanceof Executable) ? ((Executable) member).getParameterTypes() : null;

        if (parameterTypes != null) {
            for (Class<?> paramType : parameterTypes) {
                String name = getName(paramType);
                result.add(name);
            }
        }

        return result;
    }

    @Override
    public List<String> getClassAnnotationNames(Class aClass) {
        return getAnnotationNames(aClass.getDeclaredAnnotations());
    }

    @Override
    public List<String> getFieldAnnotationNames(Field field) {
        return getAnnotationNames(field.getDeclaredAnnotations());
    }

    @Override
    public List<String> getMethodAnnotationNames(Member method) {
        Annotation[] annotations = (method instanceof Executable)
                                   ? ((Executable) method).getDeclaredAnnotations()
                                   : null;
        return getAnnotationNames(annotations);
    }

    @Override
    public List<String> getParameterAnnotationNames(Member method, int parameterIndex) {
        Annotation[][] annotations = (method instanceof Executable)
                                     ? ((Executable) method).getParameterAnnotations()
                                     : null;

        return (annotations != null) ? getAnnotationNames(annotations[parameterIndex]) : getAnnotationNames(null);
    }

    @Override
    public String getReturnTypeName(Member method) {
        return ((Method) method).getReturnType().getName();
    }

    @Override
    public String getFieldName(Field field) {
        return field.getName();
    }

    @Override
    public Class getOrCreateClassObject(File file) {
        return getOrCreateClassObject(file, null);
    }

    public static Class getOrCreateClassObject(File file, @Nullable ClassLoader... loaders) {
        String name = file.getRelativePath().replace("/", ".").replace(".class", "");
        return forName(name, loaders);
    }

    @Override
    public String getMethodModifier(Member method) {
        return Modifier.toString(method.getModifiers());
    }

    @Override
    public String getMethodKey(Class cls, Member method) {
        return getMethodName(method) + '(' + Joiner.on(", ").join(getParameterNames(method)) + ')';
    }

    @Override
    public String getMethodFullKey(Class cls, Member method) {
        return getClassName(cls) + '.' + getMethodKey(cls, method);
    }

    @Override
    public boolean isPublic(Object o) {
        Integer mod = (o instanceof Class)
                      ? ((Class) o).getModifiers()
                      : ((o instanceof Member) ? ((Member) o).getModifiers() : null);

        return (mod != null) && Modifier.isPublic(mod);
    }

    @Override
    public String getClassName(Class cls) {
        return cls.getName();
    }

    @Override
    public String getSuperclassName(Class cls) {
        Class superclass = cls.getSuperclass();
        return (superclass != null) ? superclass.getName() : "";
    }

    @Override
    public List<String> getInterfacesNames(Class cls) {
        Class[]      classes = cls.getInterfaces();
        List<String> names;
        names = (classes != null) ? new ArrayList<>(classes.length) : new ArrayList<>(0);
        if (classes != null) {
            for (Class cls1 : classes) {
                names.add(cls1.getName());
            }
        }
        return names;
    }

    @Override
    public boolean acceptsInput(String file) {
        return file.endsWith(".class");
    }

    //
    private static List<String> getAnnotationNames(Annotation[] annotations) {
        List<String> names = new ArrayList<>(annotations.length);
        for (Annotation annotation : annotations) {
            names.add(annotation.annotationType().getName());
        }
        return names;
    }

    public static String getName(Class type) {
        if (type.isArray()) {
            try {
                Class cl  = type;
                int   dim = 0;
                while (cl.isArray()) {
                    dim++;
                    cl = cl.getComponentType();
                }
                return cl.getName() + Utils.repeat("[]", dim);
            } catch (Throwable e) {
                //
            }
        }
        return type.getName();
    }
}
