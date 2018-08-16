package org.reflections.adapters;

import org.reflections.vfs.Vfs.File;

import java.util.List;

/**
 *
 */
public interface MetadataAdapter<C, F, M> {

    //
    String getClassName(C cls);

    String getSuperclassName(C cls);

    List<String> getInterfacesNames(C cls);

    //
    List<F> getFields(C cls);

    List<M> getMethods(C cls);

    String getMethodName(M method);

    List<String> getParameterNames(M method);

    List<String> getClassAnnotationNames(C aClass);

    List<String> getFieldAnnotationNames(F field);

    List<String> getMethodAnnotationNames(M method);

    List<String> getParameterAnnotationNames(M method, int parameterIndex);

    String getReturnTypeName(M method);

    String getFieldName(F field);

    C getOrCreateClassObject(File file);

    String getMethodModifier(M method);

    String getMethodKey(C cls, M method);

    String getMethodFullKey(C cls, M method);

    boolean isPublic(Object o);

    boolean acceptsInput(String file);

}
