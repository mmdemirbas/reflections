package org.reflections.scanners;

import java.util.List;

/**
 * scans for method's annotations
 */
@SuppressWarnings("unchecked")
public class MethodAnnotationsScanner extends AbstractScanner {

    @Override
    public void scan(Object cls) {
        for (Object method : getMetadataAdapter().getMethods(cls)) {
            for (String methodAnnotation : (List<String>) getMetadataAdapter().getMethodAnnotationNames(method)) {
                if (acceptResult(methodAnnotation)) {
                    getStore().put(methodAnnotation, getMetadataAdapter().getMethodFullKey(cls, method));
                }
            }
        }
    }
}
