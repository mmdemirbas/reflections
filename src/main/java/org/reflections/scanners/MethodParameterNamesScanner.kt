package org.reflections.scanners

import com.google.common.base.Joiner
import javassist.bytecode.LocalVariableAttribute
import org.reflections.ClassWrapper
import org.reflections.JavassistMethodWrapper
import java.lang.reflect.Modifier
import java.util.*

/**
 * scans methods/constructors and indexes parameter names
 */
class MethodParameterNamesScanner : AbstractScanner() {

    override fun scan(cls: ClassWrapper) {
        val md = metadataAdapter

        for (method in md.getMethods(cls)) {
            val key = md.getMethodFullKey(cls, method)
            if (acceptResult(key)) {
                val javassistMethodWrapper = method as JavassistMethodWrapper
                val delegate = javassistMethodWrapper.delegate
                val codeAttribute = delegate.codeAttribute
                val attribute = codeAttribute?.getAttribute(LocalVariableAttribute.tag)
                val table = attribute as LocalVariableAttribute?
                val length = table?.tableLength() ?: 0
                var i = if (Modifier.isStatic(method.delegate.accessFlags)) 0 else 1 //skip this
                if (i < length) {
                    val names = ArrayList<String>(length - i)
                    while (i < length) {
                        names.add(method.delegate.constPool.getUtf8Info(table!!.nameIndex(i++)))
                    }
                    store!!.put(key, Joiner.on(", ").join(names))
                }
            }
        }
    }
}
