package org.reflections.adapters;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import javassist.bytecode.*;
import javassist.bytecode.Descriptor.Iterator;
import javassist.bytecode.annotation.Annotation;
import org.reflections.ReflectionsException;
import org.reflections.util.Utils;
import org.reflections.vfs.Vfs;

import java.io.*;
import java.util.Arrays;
import java.util.List;

import static javassist.bytecode.AccessFlag.isPrivate;
import static javassist.bytecode.AccessFlag.isProtected;

/**
 *
 */
public class JavassistAdapter implements MetadataAdapter<ClassFile, FieldInfo, MethodInfo> {

    /**
     * setting this to false will result in returning only visible annotations from the relevant methods here (only {@link java.lang.annotation.RetentionPolicy#RUNTIME})
     */
    public static final boolean includeInvisibleTag = true;

    @Override
    public List<FieldInfo> getFields(ClassFile cls) {
        //noinspection unchecked
        return cls.getFields();
    }

    @Override
    public List<MethodInfo> getMethods(ClassFile cls) {
        //noinspection unchecked
        return cls.getMethods();
    }

    @Override
    public String getMethodName(MethodInfo method) {
        return method.getName();
    }

    @Override
    public List<String> getParameterNames(MethodInfo method) {
        String descriptor = method.getDescriptor();
        descriptor = descriptor.substring(descriptor.indexOf('(') + 1, descriptor.lastIndexOf(')'));
        return splitDescriptorToTypeNames(descriptor);
    }

    @Override
    public List<String> getClassAnnotationNames(ClassFile aClass) {
        return includeInvisibleTag
               ? getAnnotationNames((AnnotationsAttribute) aClass.getAttribute(AnnotationsAttribute.visibleTag),
                                    (AnnotationsAttribute) aClass.getAttribute(AnnotationsAttribute.invisibleTag))
               : getAnnotationNames((AnnotationsAttribute) aClass.getAttribute(AnnotationsAttribute.visibleTag), null);
    }

    @Override
    public List<String> getFieldAnnotationNames(FieldInfo field) {
        return includeInvisibleTag
               ? getAnnotationNames((AnnotationsAttribute) field.getAttribute(AnnotationsAttribute.visibleTag),
                                    (AnnotationsAttribute) field.getAttribute(AnnotationsAttribute.invisibleTag))
               : getAnnotationNames((AnnotationsAttribute) field.getAttribute(AnnotationsAttribute.visibleTag), null);
    }

    @Override
    public List<String> getMethodAnnotationNames(MethodInfo method) {
        return includeInvisibleTag
               ? getAnnotationNames((AnnotationsAttribute) method.getAttribute(AnnotationsAttribute.visibleTag),
                                    (AnnotationsAttribute) method.getAttribute(AnnotationsAttribute.invisibleTag))
               : getAnnotationNames((AnnotationsAttribute) method.getAttribute(AnnotationsAttribute.visibleTag), null);
    }

    @Override
    public List<String> getParameterAnnotationNames(MethodInfo method, int parameterIndex) {
        List<String> result = Lists.newArrayList();

        List<ParameterAnnotationsAttribute> parameterAnnotationsAttributes = Lists.newArrayList((ParameterAnnotationsAttribute) method
                                                                                                        .getAttribute(ParameterAnnotationsAttribute.visibleTag),
                                                                                                (ParameterAnnotationsAttribute) method
                                                                                                        .getAttribute(
                                                                                                                ParameterAnnotationsAttribute.invisibleTag));

        if (parameterAnnotationsAttributes != null) {
            for (ParameterAnnotationsAttribute parameterAnnotationsAttribute : parameterAnnotationsAttributes) {
                if (parameterAnnotationsAttribute != null) {
                    Annotation[][] annotations = parameterAnnotationsAttribute.getAnnotations();
                    if (parameterIndex < annotations.length) {
                        Annotation[] annotation = annotations[parameterIndex];
                        result.addAll(getAnnotationNames(annotation));
                    }
                }
            }
        }

        return result;
    }

    @Override
    public String getReturnTypeName(MethodInfo method) {
        String descriptor = method.getDescriptor();
        descriptor = descriptor.substring(descriptor.lastIndexOf(')') + 1);
        return splitDescriptorToTypeNames(descriptor).get(0);
    }

    @Override
    public String getFieldName(FieldInfo field) {
        return field.getName();
    }

    @Override
    public ClassFile getOrCreateClassObject(Vfs.File file) {
        InputStream inputStream = null;
        try {
            inputStream = file.openInputStream();
            DataInputStream dis = new DataInputStream(new BufferedInputStream(inputStream));
            return new ClassFile(dis);
        } catch (IOException e) {
            throw new ReflectionsException("could not create class file from " + file.getName(), e);
        } finally {
            Utils.close(inputStream);
        }
    }

    @Override
    public String getMethodModifier(MethodInfo method) {
        int accessFlags = method.getAccessFlags();
        return isPrivate(accessFlags)
               ? "private"
               : (isProtected(accessFlags) ? "protected" : (isPublic(accessFlags) ? "public" : ""));
    }

    @Override
    public String getMethodKey(ClassFile cls, MethodInfo method) {
        return getMethodName(method) + '(' + Joiner.on(", ").join(getParameterNames(method)) + ')';
    }

    @Override
    public String getMethodFullKey(ClassFile cls, MethodInfo method) {
        return getClassName(cls) + '.' + getMethodKey(cls, method);
    }

    @Override
    public boolean isPublic(Object o) {
        Integer accessFlags = (o instanceof ClassFile)
                              ? ((ClassFile) o).getAccessFlags()
                              : ((o instanceof FieldInfo)
                                 ? ((FieldInfo) o).getAccessFlags()
                                 : ((o instanceof MethodInfo) ? ((MethodInfo) o).getAccessFlags() : null));

        return (accessFlags != null) && AccessFlag.isPublic(accessFlags);
    }

    //
    @Override
    public String getClassName(ClassFile cls) {
        return cls.getName();
    }

    @Override
    public String getSuperclassName(ClassFile cls) {
        return cls.getSuperclass();
    }

    @Override
    public List<String> getInterfacesNames(ClassFile cls) {
        return Arrays.asList(cls.getInterfaces());
    }

    @Override
    public boolean acceptsInput(String file) {
        return file.endsWith(".class");
    }

    //
    private static List<String> getAnnotationNames(AnnotationsAttribute... annotationsAttributes) {
        List<String> result = Lists.newArrayList();

        if (annotationsAttributes != null) {
            for (AnnotationsAttribute annotationsAttribute : annotationsAttributes) {
                if (annotationsAttribute != null) {
                    for (Annotation annotation : annotationsAttribute.getAnnotations()) {
                        result.add(annotation.getTypeName());
                    }
                }
            }
        }

        return result;
    }

    private static List<String> getAnnotationNames(Annotation[] annotations) {
        List<String> result = Lists.newArrayList();

        for (Annotation annotation : annotations) {
            result.add(annotation.getTypeName());
        }

        return result;
    }

    private static List<String> splitDescriptorToTypeNames(String descriptors) {
        List<String> result = Lists.newArrayList();

        if ((descriptors != null) && !descriptors.isEmpty()) {

            List<Integer> indices  = Lists.newArrayList();
            Iterator      iterator = new Iterator(descriptors);
            while (iterator.hasNext()) {
                indices.add(iterator.next());
            }
            indices.add(descriptors.length());

            for (int i = 0; i < (indices.size() - 1); i++) {
                String s1 = Descriptor.toString(descriptors.substring(indices.get(i), indices.get(i + 1)));
                result.add(s1);
            }

        }

        return result;
    }
}
