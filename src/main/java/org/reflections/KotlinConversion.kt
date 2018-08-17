package org.reflections

import javassist.bytecode.ClassFile
import javassist.bytecode.FieldInfo
import javassist.bytecode.MethodInfo
import java.lang.reflect.Field
import java.lang.reflect.Member


interface ClassWrapper
interface FieldWrapper
interface MethodWrapper


data class JavassistClassWrapper(val delegate: ClassFile) : ClassWrapper
data class JavassistFieldWrapper(val delegate: FieldInfo) : FieldWrapper
data class JavassistMethodWrapper(val delegate: MethodInfo) : MethodWrapper


data class JavaReflectionClassWrapper(val delegate: Class<*>) : ClassWrapper
data class JavaReflectionFieldWrapper(val delegate: Field) : FieldWrapper
data class JavaReflectionMethodWrapper(val delegate: Member) : MethodWrapper


