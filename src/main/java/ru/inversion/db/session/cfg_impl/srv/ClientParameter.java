package ru.inversion.db.session.cfg_impl.srv;

import java.net.InetAddress;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;


public enum ClientParameter {

    HOST_NAME("X-Client-Host", () -> {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return null;
        }
    }),

    /** */
    APP_NAME("X-Client-App-Name", new Supplier<String>() {
        @Override
        public String get() {
            return globalValueSupplier().apply(APP_NAME);
        }
    }),

    /** */
    APP_KIND("X-Client-App-Kind", new Supplier<String>() {
        @Override
        public String get() {
            return globalValueSupplier().apply(APP_KIND);
        }
    }),

    APP_VERSION("X-Client-App-Version", new Supplier<String>() {
        @Override
        public String get() {
            return globalValueSupplier().apply(APP_VERSION);
        }
    }),

    APP_PLATFORM("X-Client-App-Platform", () -> System.getProperty("java.version")),

    OS_NAME("X-Client-OS-Name", ()->System.getProperty("os.name")),

    OS_VERSION("X-Client-OS-Version", ()->System.getProperty("os.version")),

    OS_ARCH("X-Client-OS-Arch", ()->System.getProperty("os.arch")),

    OS_USER("X-Client-OS-User", ()->System.getProperty("user.name"));

    private final String headerName;
    private final Supplier<String> resolver;

    ClientParameter(String headerName, Supplier<String> resolver ) {
        this.headerName = headerName;
        this.resolver   = resolver;
    }

    /** */
    public String headerName() {
        return headerName;
    }

    /** */
    public String resolve() {
        return resolver.get();
    }

    private static EnumMap<ClientParameter,String> cachedValues = null;

    private static Function<ClientParameter,String> globalValueSupplier;

    synchronized public static void initGlobalValueSupplier( Function<ClientParameter,String> gvs )
    {
        cachedValues = null;
        globalValueSupplier = gvs;
    }

    /** */
    static Function<ClientParameter,String> globalValueSupplier() {
        if( globalValueSupplier == null )
            throw new IllegalStateException("'globalValueSupplier' is not initialized");
        return globalValueSupplier;
    }

    /** */
    synchronized public static void fillHeaders( BiConsumer<String,String> assigner )
    {
        if( assigner == null )
            return;

        if( cachedValues == null ) {
            cachedValues = new EnumMap<>(ClientParameter.class);
            for( ClientParameter p : ClientParameter.values() ) {
                 String v = p.resolve();
                 if( v == null || v.trim().isEmpty() )
                     continue;
                 cachedValues.put( p, v );
            }
        }

        cachedValues.forEach( (k,v)->assigner.accept(k.headerName,v) );
    }

    private static String detectOsKind(String osName) {

        if( osName == null )
            return null;

        String s = osName.toLowerCase();
        if (s.contains("win")) return "windows";
        if (s.contains("mac")) return "macos";
        if (s.contains("nix") || s.contains("nux") || s.contains("aix")) return "unix";
        return "other";
    }
}