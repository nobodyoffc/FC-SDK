package Exceptions;

public class ExceptionTools {
    public static void throwRunTimeException(String msg) {
        RuntimeException runtimeException = new RuntimeException(msg);
        runtimeException.printStackTrace();
        throw runtimeException;
    }
}
