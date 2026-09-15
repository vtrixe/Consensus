package com.example.consensus.tenant;

public class TenantContext {

    private static final ThreadLocal<String> SCHEMA = new ThreadLocal<>();

    public static void set(String schema)  { SCHEMA.set(schema); }
    public static String get()             { return SCHEMA.get(); }
    public static void clear()             { SCHEMA.remove(); }
}
