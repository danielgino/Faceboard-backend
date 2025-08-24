package org.example.apimywebsite.configuration;


import java.util.regex.Pattern;

public final class PasswordPolicy {
    private PasswordPolicy() {}public static final String REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-={}:\";'<>?,./]).{8,}$";

    public static final String MESSAGE =
            "Password must include upper/lowercase letters, a number, and a special character (min 8 chars)";

    public static final Pattern COMPILED = Pattern.compile(REGEX);

    public static boolean isValid(String s) {
        return s != null && COMPILED.matcher(s).matches();
    }
}
