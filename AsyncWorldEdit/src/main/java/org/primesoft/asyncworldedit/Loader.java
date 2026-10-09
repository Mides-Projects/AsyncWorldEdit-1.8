/*
 * AsyncWorldEdit a performance improvement plugin for Minecraft WorldEdit plugin.
 * Copyright (c) 2016, SBPrime <https://github.com/SBPrime/>
 * Copyright (c) AsyncWorldEdit contributors
 *
 * All rights reserved.
 *
 * Redistribution in source, use in source and binary forms, with or without
 * modification, are permitted free of charge provided that the following 
 * conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 * 2.  Redistributions of source code, with or without modification, in any form
 *     other then free of charge is not allowed,
 * 3.  Redistributions of source code, with tools and/or scripts used to build the 
 *     software is not allowed,
 * 4.  Redistributions of source code, with information on how to compile the software
 *     is not allowed,
 * 5.  Providing information of any sort (excluding information from the software page)
 *     on how to compile the software is not allowed,
 * 6.  You are allowed to build the software for your personal use,
 * 7.  You are allowed to build the software using a non public build server,
 * 8.  Redistributions in binary form in not allowed.
 * 9.  The original author is allowed to redistrubute the software in bnary form.
 * 10. Any derived work based on or containing parts of this software must reproduce
 *     the above copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided with the
 *     derived work.
 * 11. The original author of the software is allowed to change the license
 *     terms or the entire license of the software as he sees fit.
 * 12. The original author of the software is allowed to sublicense the software
 *     or its parts using any license terms he sees fit.
 * 13. By contributing to this project you agree that your contribution falls under this
 *     license.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.primesoft.asyncworldedit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import static org.primesoft.asyncworldedit.LoggerProvider.log;
import org.primesoft.asyncworldedit.api.inner.IAsyncWorldEditCore;
import org.primesoft.asyncworldedit.api.inner.IAwePlugin;
import org.primesoft.asyncworldedit.api.inner.ILibraryLoader;
import org.primesoft.asyncworldedit.injector.InjectorManual;
import org.primesoft.asyncworldedit.utils.Reflection;

/**
 *
 * @author SBPrime
 */
public abstract class Loader extends ClassLoader implements ILibraryLoader {

    private final static double INJECTOR_MIN = 1.05;
    private final static double INJECTOR_MAX = 1.06;

    private final static String PREFIX = "org.primesoft.asyncworldedit";
    private final static String API = ".api.";
    private final static int BUF_SIZE = 0x10000;

    protected final static String PLUGIN_INJECTOR = "AsyncWorldEditInjector";
    private final static String PLUGIN_INJECTOR_JAR = "AsyncWorldEditInjector.jar";
    private final static String INSTALLED = "installed";
    private final static String LICENSE = "license.txt";

    protected final static String PLUGINS = "plugins";
    protected final static String DISABLED = ".dis";

    private final static int DIR_LEN = 12;
    private final static Charset UTF8 = Charset.forName("UTF-8");

    /**
     * The addURL method in URLClassLoader
     */
    private final static Method s_addUrl;

    /**
     * the findLoadedClass in ClassLoader
     */
    private final static Method s_findLoadedClass;

    static {
        s_findLoadedClass = Reflection.findMethod(ClassLoader.class,
                "findLoadedClass", "Unable to find findLoadedClass method on ClassLoader", String.class);
        s_addUrl = Reflection.findMethod(URLClassLoader.class, "addURL", "Unable to fine addURL method", URL.class);
    }

    /**
     * The name SEED
     */
    private byte[] m_seed = null;

    /**
     * List of all loaded plugins
     */
    private final List<IAwePlugin> m_loadedPlugins;

    /**
     * Parrent class loader
     */
    private final ClassLoader m_classLoader;

    /**
     * The list of known classes
     */
    private final Map<String, Class<?>> m_classHash;

    /**
     * The current class
     */
    private final Class<?> m_thisClass;

    /**
     * The encryption key
     */
    private SecretKeySpec m_key;

    /**
     * The SHA256 algorith
     */
    private final MessageDigest m_sha;

    /**
     * List of loaded libraries
     */
    private final Map<String, Map<String, byte[]>> m_libraries;

    protected Loader(ClassLoader classLoader) {
        super(classLoader);

        m_classLoader = classLoader;
        m_thisClass = Loader.class;
        m_classHash = new LinkedHashMap<String, Class<?>>();
        m_libraries = new LinkedHashMap<String, Map<String, byte[]>>();
        m_loadedPlugins = new LinkedList<IAwePlugin>();

        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            sha = null;
        }

        m_sha = sha;
    }

    /**
     * Check if all dependencies are loaded
     * @return 
     */
    abstract boolean checkDependencies();
    
    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // JDK classes must never be (re)defined by this loader: defining
        // java.* is prohibited (SecurityException: Prohibited package name)
        // and on Java 9+ the boot classes are even visible to plain resource
        // lookups (jrt:/). Delegate straight to the parent.
        if (name != null && (name.startsWith("java.") || name.startsWith("jdk."))) {
            return super.loadClass(name, resolve);
        }

        Class<?> cls = null;

        // Classes already defined by this loader (in-memory libraries and
        // our own previously defined classes).
        if (m_classHash.containsKey(name)) {
            return m_classHash.get(name);
        }

        String fileName = String.format("%1$s.class", name.replace('.', '/'));

        try {
            // In-memory libraries first: the adapter and injector shadows live
            // only here (nested jars), the parent can never provide them.
            cls = loadFromLibrary(name, fileName);
            if (cls != null) {
                return cls;
            }

            // Parent/global next: everything the parent can provide (including
            // our own fat-jar classes) must come from there. Defining a local
            // duplicate creates a parallel universe and cross-loader casts
            // (e.g. BukkitAsyncWorldEditCore to IAsyncWorldEditCore) fail with
            // ClassCastException. super.loadClass also performs the
            // already-loaded check internally, so no reflective probe needed.
            try {
                cls = super.loadClass(name, resolve);
                return cls;
            } catch (ClassNotFoundException ex) {
                // Not visible to the parent: fall through to local define.
                cls = null;
            }

            // Local define is only meaningful for our own namespace; anything
            // else should have resolved above.
            if (name == null || !name.startsWith("org.primesoft.asyncworldedit.")) {
                throw new ClassNotFoundException(name);
            }

            InputStream is = getResourceStream(fileName);
            if (is == null) {
                cls = super.loadClass(name, resolve);
                return cls;
            }

            byte[] data = readFully(is);
            SecretKeySpec key = m_key;
            if (key == null) {
                try {
                    String aes = "AES cipher here";
                    String pub = "PUB Key 1"
                            + "PUB Key 2"
                            + "PUB Key 3"
                            + "PUB Key 4"
                            + "PUB Key 5";
                    int len = pub.length() / 5;
                    int pos = pub.indexOf('=');
                    String part1 = pub.substring(len * 0, len * 1);
                    String part2 = pub.substring(len * 1, len * 2);
                    String part3 = pub.substring(len * 2, len * 3);
                    String part4 = pub.substring(len * 3, len * 4);
                    String part5 = pos < 0 ? "" : pub.substring(len * 4, pos);

                    boolean hasMatch = true;
                    int iMatch = 0;
                    int iSkip = 0;

                    for (int match = 1; match < len && hasMatch; match++) {
                        final String toMatch = part1.substring(len - match);

                        hasMatch = false;
                        for (int skip = iSkip; skip < len - match; skip++) {
                            final String txt = part2.substring(len - match - skip, len - skip);

                            if (txt.equals(toMatch)) {
                                hasMatch = true;
                                iMatch = match;
                                iSkip = skip;
                                break;
                            }
                        }
                    }

                    String sSkip = part2.substring(len - iSkip);
                    String sUser = part1.substring(len - iMatch);

                    StringBuilder sb = new StringBuilder();
                    sb.append(part1.replaceAll(sUser, "").replaceAll(sSkip, ""));
                    sb.append(part2.replaceAll(sUser, "").replaceAll(sSkip, ""));
                    sb.append(part3.replaceAll(sUser, "").replaceAll(sSkip, ""));
                    sb.append(part4.replaceAll(sUser, "").replaceAll(sSkip, ""));
                    sb.append(part5.replaceAll(sUser, "").replaceAll(sSkip, ""));

                    KeyFactory factory = KeyFactory.getInstance("RSA");                    
                    PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(sb.toString())));

                    Cipher cipher = Cipher.getInstance("RSA");
                    cipher.init(Cipher.DECRYPT_MODE, publicKey);

                    byte[] keyData = cipher.doFinal(Base64.getDecoder().decode(aes));
                    byte[] aesKey = new byte[keyData.length / 2];
                    for (int i = 0; i < aesKey.length; i++) {
                        aesKey[i] = (byte) (keyData[i * 2 + 0] ^ keyData[i * 2 + 1]);
                    }
                    m_key = new SecretKeySpec(aesKey, "AES");
                } catch (NoSuchAlgorithmException ex) {                    
                    return null;
                } catch (InvalidKeySpecException ex) {
                    return null;
                } catch (NoSuchPaddingException ex) {
                    return null;
                } catch (InvalidKeyException ex) {
                    return null;
                } catch (IllegalBlockSizeException ex) {
                    return null;
                } catch (BadPaddingException ex) {
                    return null;
                } catch (RuntimeException ex) {
                    // Community/JDK25 build: no license key material (the key
                    // derivation placeholders cannot produce a valid key).
                    // Leave m_key null so the class bytes below are defined
                    // plain; the AES init failure is already ignored by the
                    // catches that follow.
                }

                key = m_key;
            }

            if (data == null) {
                return null;
            }

            try {
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, key);

                data = cipher.doFinal(data);
            } catch (NoSuchAlgorithmException ex) {
            } catch (NoSuchPaddingException ex) {
            } catch (InvalidKeyException ex) {
            } catch (IllegalBlockSizeException ex) {
            } catch (BadPaddingException ex) {
            }

            cls = super.defineClass(name, data, 0, data.length);
            return cls;
        } finally {
            if (cls != null) {
                m_classHash.put(name, cls);
            }
        }
    }

    /**
     * Read input stream to byte array
     *
     * @param is
     * @return
     * @throws IOException
     */
    private static byte[] readFully(InputStream is) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int nRead;
            byte[] data = new byte[BUF_SIZE];

            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();

            return buffer.toByteArray();
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Returns the class with the given <a href="#name">binary name</a> if this
     * loader has been recorded by the Java virtual machine as an initiating
     * loader of a class with that <a href="#name">binary name</a>. Otherwise
     * <tt>null</tt> is returned.  </p>
     *
     * @param classLoader
     * @param name The name of the class
     * @return
     */
    private static Class<?> findLoadedClass(ClassLoader classLoader, String name) {
        if (s_findLoadedClass == null) {
            return null;
        }

        return Reflection.invoke(classLoader, Class.class, s_findLoadedClass,
                "Unable to invoke findLoadedClass", name);
    }

    /**
     * Get the main plugin folder
     * @return 
     */
    protected abstract File getPluginFolder();
    
    /**
     * Get the configuration folder
     * @return 
     */
    protected abstract File getDataFolder();
    
    /**
     * Install the AWE plugins
     * @return 
     */
    protected abstract boolean installPlugins();
    
    /**
     * Install the AWE plugin
     *
     * @param server
     */
    boolean install() {
        File dataFolder = getDataFolder();
        File pluginFolder = getPluginFolder();

        if (!dataFolder.exists()) {
            if (!dataFolder.mkdirs()) {
                return false;
            }
        }

        if (!checkInjector()) {
            log("AsyncWorldEdit Injector not detected. Installing...");
            if (!extract(PLUGIN_INJECTOR_JAR, pluginFolder, PLUGIN_INJECTOR_JAR)) {
                log("ERROR: Unable to extract the Injector");
                return false;
            }

            boolean injected;
            try {
                injected = injectClass(new File(pluginFolder, PLUGIN_INJECTOR_JAR));
            } catch (RuntimeException ex) {
                // Java 16+ strong encapsulation: URLClassLoader.addURL reflection
                // throws InaccessibleObjectException (module java.base does not
                // open java.net). Fall through to the in-loader fallback below.
                // NOTE: for the full classloader-shadowing behavior the server
                // should be started with:
                //   --add-opens java.base/java.net=ALL-UNNAMED
                log("Standard injector injection unavailable (no --add-opens java.base/java.net=ALL-UNNAMED?), using in-loader fallback...");
                injected = false;
            }

            if (!injected) {
                // Fallback for modern Java: load the injector from our own
                // resources straight into this loader (ClassLoader.defineClass
                // needs no --add-opens) and enable it without static references
                // (the plugin class loader cannot see the nested jar).
                if (!loadLibrary(PLUGIN_INJECTOR_JAR)) {
                    log("ERROR: Unable to load the injector");
                    return false;
                }

                if (!enableInjectorManual()) {
                    log("ERROR: Unable to enable the injector");
                    return false;
                }
            } else {
                new InjectorManual().onEnable();
            }
        }

        File installed = new File(dataFolder, INSTALLED);
        if (installed.exists()) {
            log("Plugin installed");
            return true;
        }
        
        extract(LICENSE, dataFolder, LICENSE);
        
        if (!installPlugins()) {
            return false;
        }

        try {
            installed.createNewFile();
        } catch (IOException ex) {
            log("ERROR: Unable set installed flag.");
            return false;
        }

        return true;
    }

    /**
     * Extract the resource
     *
     * @param resource
     * @param targetDir
     * @param targetName
     * @return
     */
    protected boolean extract(String resource, File targetDir, String targetName) {
        InputStream is = getResourceStream(resource);

        if (is == null) {
            return false;
        }

        try {
            FileOutputStream os = new FileOutputStream(new File(targetDir, targetName));

            int nRead;
            byte[] data = new byte[BUF_SIZE];

            while ((nRead = is.read(data, 0, data.length)) != -1) {
                os.write(data, 0, nRead);
            }

            os.flush();
            os.close();

            return true;
        } catch (IOException ex) {
            log("Unable to extract resource.");
            return false;
        }
    }

    private InputStream getResourceStream(final String fileName) {
        if (fileName == null) {
            return null;
        }

        // Community/JDK25 build: resources are stored under plain names.
        // The original /res obfuscation requires the private JarProcessor and
        // the seed patches (src/main/patches) which are not redistributable,
        // so the placeholder seed would crash with
        // StringIndexOutOfBoundsException here. Absolute lookup from the
        // classpath root covers nested jars (AsyncWorldEditInjector.jar,
        // AsyncWorldEdit-spigot_v1_8_R3.jar), LICENSE and .class files.
        String normalized = fileName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return null;
        }

        return m_thisClass.getResourceAsStream("/" + normalized);
    }

    /**
     * Enables the manually loaded injector without referencing its classes
     * statically (they live in the nested injector jar which the plugin class
     * loader cannot see when URLClassLoader.addURL reflection is blocked on
     * modern Java). Resolved through this loader, which defines them from
     * memory via {@link #loadLibrary(String)}.
     *
     * @return true when the injector was enabled
     */
    private boolean enableInjectorManual() {
        try {
            Class<?> cls = loadClass("org.primesoft.asyncworldedit.injector.InjectorManual");
            Object manual = cls.getDeclaredConstructor().newInstance();
            cls.getMethod("onEnable").invoke(manual);
            return true;
        } catch (Throwable ex) {
            log("ERROR: Unable to enable the injector: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Inject external JAR libraries to class loader
     *
     * @param libFile
     * @return
     */
    private boolean injectClass(File libFile) {
        final URL libUrl;

        if (s_addUrl == null) {
            return false;
        }

        try {
            libUrl = new URL(String.format("jar:%1$s!/", libFile.toURI().toURL().toExternalForm()));
        } catch (MalformedURLException ex) {
            log("Unable to load external jar");
            return false;
        }

        return Reflection.invoke(m_classLoader, s_addUrl, "Unable to load library", libUrl);
    }

    /**
     * Try to load the AWE plugin
     * @param pluginFile 
     * @return  
     */
    protected abstract IAwePlugin loadPlugin(File pluginFile);
    
    /**
     * Load the AWE plugins
     */
    void loadPlugins(IAsyncWorldEditCore api) {
        log("Loading plugins...");

        File dataFolder = getDataFolder();
        File pluginDir = new File(dataFolder, PLUGINS);

        if (!pluginDir.exists() || !pluginDir.canRead()) {
            return;
        }

        File[] enabledPlugins = pluginDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name != null && name.toLowerCase().endsWith(".jar");
            }
        });

        for (File pluginFile : enabledPlugins) {
            IAwePlugin plugin = loadPlugin(pluginFile);
            if (plugin != null) {
                m_loadedPlugins.add(plugin);
                
                plugin.initialize(api);
            }
        }
    }

    /**
     * Unload the plugin
     * @param plugin 
     */
    protected abstract void unloadPlugin(IAwePlugin plugin);
    
    /**
     * Unload the plugins
     */
    void unloadPlugins() {
        log("Unloading plugins...");

        for (IAwePlugin plugin : m_loadedPlugins) {
            unloadPlugin(plugin);
        }

        m_loadedPlugins.clear();
    }

    /**
     * Load class from libraries
     *
     * @param className
     * @param fileName
     * @return
     */
    private synchronized Class<?> loadFromLibrary(String className, String fileName) {
        byte[] data = null;
        for (Map<String, byte[]> libFile : m_libraries.values()) {
            if (libFile.containsKey(fileName)) {
                data = libFile.get(fileName);
                break;
            }
        }

        if (data == null) {
            return null;
        }

        return super.defineClass(className, data, 0, data.length);
    }

    @Override
    public synchronized boolean loadLibrary(String library) {
        if (m_libraries.containsKey(library)) {
            return false;
        }

        InputStream is = getResourceStream(library);
        if (is == null) {
            log(String.format("Ups unable to open library %1$s", library));
            return false;
        }

        Map<String, byte[]> files = new LinkedHashMap<String, byte[]>();

        try {
            JarInputStream jarStream = new JarInputStream(is);
            ZipEntry entry;

            while ((entry = jarStream.getNextEntry()) != null) {
                byte[] data = readFully(jarStream);
                if (data == null || data.length == 0) {
                    continue;
                }

                files.put(entry.getName(), data);
            }
            is.close();
        } catch (IOException ex) {
            log(String.format("Ups unable to load library %1$s", library));
            return false;
        }

        m_libraries.put(library, files);
        return true;
    }

    @Override
    public synchronized boolean unloadLibrary(String library) {
        if (m_libraries.containsKey(library)) {
            return false;
        }

        m_libraries.remove(library);

        return true;
    }

    /**
     * Try to get the injector version
     *
     * @return Returns the injector version or -1 if not found
     */
    protected abstract double getInjectorVersion();

    /**
     * Check if the injector is installed
     *
     * @return
     */
    private boolean checkInjector() {
        double version = getInjectorVersion();

        if (version < 0) {
            return false;
        }
        
        if (version < INJECTOR_MIN || version >= INJECTOR_MAX) {
            log(String.format("Invalid injecotr version. Current version: %1$s. Valid version: <%2$s, %3$s).",
                    version, INJECTOR_MIN, INJECTOR_MAX));
            return false;
        }

        return true;
    }
}
