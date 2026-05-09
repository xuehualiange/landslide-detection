package com.graduation.landslide.auth;

public class AuthContext {
    private static final ThreadLocal<String> USER_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE_HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(String username, String role) {
        USER_HOLDER.set(username);
        ROLE_HOLDER.set(role);
    }

    public static String username() {
        return USER_HOLDER.get();
    }

    public static String role() {
        return ROLE_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
        ROLE_HOLDER.remove();
    }
}
