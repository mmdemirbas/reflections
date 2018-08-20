package org.reflections.scanners

import javassist.CannotCompileException
import javassist.ClassPool
import javassist.CtBehavior
import javassist.LoaderClassPath
import javassist.NotFoundException
import javassist.bytecode.MethodInfo
import javassist.expr.ConstructorCall
import javassist.expr.ExprEditor
import javassist.expr.FieldAccess
import javassist.expr.MethodCall
import javassist.expr.NewExpr
import org.reflections.ReflectionsException
import org.reflections.adapters.ClassAdapter
import org.reflections.adapters.JavassistMethodAdapter
import org.reflections.util.ClasspathHelper

/**
 * scans methods/constructors/fields usage
 *
 * * depends on [org.reflections.adapters.JavassistFactory] configured *
 */
class MemberUsageScanner : AbstractScanner() {

    private var classPool: ClassPool? = null

    override fun scan(cls: ClassAdapter) {
        try {
            val ctClass = getClassPool().get(cls.name)
            for (member in ctClass.declaredConstructors) {
                scanMember(member)
            }
            for (member in ctClass.declaredMethods) {
                scanMember(member)
            }
            ctClass.detach()
        } catch (e: Exception) {
            throw ReflectionsException("Could not scan method usage for " + cls.name, e)
        }

    }

    @Throws(CannotCompileException::class)
    internal fun scanMember(member: CtBehavior) {
        //key contains this$/val$ means local field/parameter closure
        val key =
                (member.declaringClass.name + '.'.toString() + member.methodInfo.name + '('.toString() + parameterNames(
                        member.methodInfo) + ')'.toString()) //+ " #" + member.getMethodInfo().getLineNumber(0)
        member.instrument(object : ExprEditor() {
            override fun edit(e: NewExpr?) {
                try {
                    put(e!!.constructor.declaringClass.name + '.'.toString() + "<init>" + '('.toString() + parameterNames(
                            e.constructor.methodInfo) + ')'.toString(), e.lineNumber, key)
                } catch (e1: NotFoundException) {
                    throw ReflectionsException("Could not find new instance usage in $key", e1)
                }

            }

            override fun edit(m: MethodCall?) {
                try {
                    put(m!!.method.declaringClass.name + '.'.toString() + m.methodName + '('.toString() + parameterNames(
                            m.method.methodInfo) + ')'.toString(), m.lineNumber, key)
                } catch (e: NotFoundException) {
                    throw ReflectionsException("Could not find member " + m!!.className + " in " + key, e)
                }

            }

            override fun edit(c: ConstructorCall?) {
                try {
                    put(c!!.constructor.declaringClass.name + '.'.toString() + "<init>" + '('.toString() + parameterNames(
                            c.constructor.methodInfo) + ')'.toString(), c.lineNumber, key)
                } catch (e: NotFoundException) {
                    throw ReflectionsException("Could not find member " + c!!.className + " in " + key, e)
                }

            }

            override fun edit(f: FieldAccess?) {
                try {
                    put(f!!.field.declaringClass.name + '.'.toString() + f.fieldName, f.lineNumber, key)
                } catch (e: NotFoundException) {
                    throw ReflectionsException("Could not find member " + f!!.fieldName + " in " + key, e)
                }

            }
        })
    }

    private fun put(key: String, lineNumber: Int, value: String) {
        if (acceptResult(key)) {
            store!!.put(key, "$value #$lineNumber")
        }
    }

    internal fun parameterNames(info: MethodInfo): String {
        return JavassistMethodAdapter(info).parameters.joinToString()
    }

    private fun getClassPool(): ClassPool {
        if (classPool == null) {
            synchronized(this) {
                classPool = ClassPool()
                var classLoaders = configuration!!.classLoaders
                if (classLoaders.isEmpty()) {
                    classLoaders = ClasspathHelper.classLoaders()
                }
                for (classLoader in classLoaders) {
                    classPool!!.appendClassPath(LoaderClassPath(classLoader))
                }
            }
        }
        return classPool!!
    }
}
