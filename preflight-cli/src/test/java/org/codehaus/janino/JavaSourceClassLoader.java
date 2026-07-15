package org.codehaus.janino;

/** Test-only classloader with the upstream Janino loader identity. */
public final class JavaSourceClassLoader extends ClassLoader {
    public JavaSourceClassLoader(ClassLoader parent) {
        super("synthetic-janino", parent);
    }

    public Class<?> defineGenerated(String name, byte[] bytecode) {
        return defineClass(name, bytecode, 0, bytecode.length);
    }
}
