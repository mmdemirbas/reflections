package org.reflections.scanners;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Multimap;
import org.reflections.Configuration;
import org.reflections.ReflectionsException;
import org.reflections.adapters.MetadataAdapter;
import org.reflections.vfs.Vfs.File;

/**
 *
 */
@SuppressWarnings("RawUseOfParameterizedType")
public abstract class AbstractScanner implements Scanner {

    private Configuration            configuration;
    private Multimap<String, String> store;
    private Predicate<String>        resultFilter = Predicates.alwaysTrue(); //accept all by default

    @Override
    public boolean acceptsInput(String file) {
        return getMetadataAdapter().acceptsInput(file);
    }

    @Override
    public Object scan(File file, Object classObject) {
        if (classObject == null) {
            try {
                classObject = configuration.getMetadataAdapter().getOrCreateClassObject(file);
            } catch (Exception e) {
                throw new ReflectionsException("could not create class object from file " + file.getRelativePath(), e);
            }
        }
        scan(classObject);
        return classObject;
    }

    public abstract void scan(Object cls);

    //
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Multimap<String, String> getStore() {
        return store;
    }

    @Override
    public void setStore(Multimap<String, String> store) {
        this.store = store;
    }

    public Predicate<String> getResultFilter() {
        return resultFilter;
    }

    public void setResultFilter(Predicate<String> resultFilter) {
        this.resultFilter = resultFilter;
    }

    @Override
    public Scanner filterResultsBy(Predicate<String> filter) {
        resultFilter = filter;
        return this;
    }

    //
    @Override
    public boolean acceptResult(String fqn) {
        return (fqn != null) && resultFilter.apply(fqn);
    }

    protected MetadataAdapter getMetadataAdapter() {
        return configuration.getMetadataAdapter();
    }

    //
    @Override
    public boolean equals(Object o) {
        return (this == o) || ((o != null) && (getClass() == o.getClass()));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
