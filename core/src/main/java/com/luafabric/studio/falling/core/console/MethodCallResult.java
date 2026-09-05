package com.luafabric.studio.falling.core.console;

/** 方法调用拦截结果。 */
public final class MethodCallResult {

    public enum Status {
        ALLOW,
        VETO,
        REPLACE
    }

    public static final MethodCallResult ALLOW = new MethodCallResult(Status.ALLOW, null);

    public final Status status;
    public final Object replaceValue;

    private MethodCallResult(Status status, Object replaceValue) {
        this.status = status;
        this.replaceValue = replaceValue;
    }

    public static MethodCallResult veto() {
        return new MethodCallResult(Status.VETO, null);
    }

    public static MethodCallResult replace(Object value) {
        return new MethodCallResult(Status.REPLACE, value);
    }
}
