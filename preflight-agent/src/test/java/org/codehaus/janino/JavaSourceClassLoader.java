package org.codehaus.janino;

import java.util.Map;

/** Test-only signature fixture; not the upstream implementation. */
public class JavaSourceClassLoader extends ClassLoader {
    protected Map<String, byte[]> generateBytecodes(String name) throws ClassNotFoundException {
        return Map.of(name, new byte[] {1});
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }
}
