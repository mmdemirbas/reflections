package org.reflections.scanners

import javassist.bytecode.LocalVariableAttribute
import org.reflections.adapters.ClassAdapter
import org.reflections.adapters.JavassistMethodAdapter
import java.lang.reflect.Modifier

/**
 * scans methods/constructors and indexes parameter names
 */
class MethodParameterNamesScanner : AbstractScanner() {

    override fun scan(cls: ClassAdapter) {
        val md = metadataAdapter

        for (method in cls.methods) {
            val key = method.getMethodFullKey(cls)
            if (acceptResult(key)) {
                val javassistMethodWrapper = method as JavassistMethodAdapter
                val delegate = javassistMethodWrapper.delegate
                val codeAttribute = delegate.codeAttribute
                val attribute = codeAttribute?.getAttribute(LocalVariableAttribute.tag)
                val table = attribute as LocalVariableAttribute?
                val length = table?.tableLength() ?: 0
                var i = if (Modifier.isStatic(method.delegate.accessFlags)) 0 else 1 //skip this
                if (i < length) {
                    val names = mutableListOf<String>()
                    while (i < length) {
                        names.add(method.delegate.constPool.getUtf8Info(table!!.nameIndex(i++)))
                    }
                    store!!.put(key, names.joinToString())
                }
            }
        }
    }
}
