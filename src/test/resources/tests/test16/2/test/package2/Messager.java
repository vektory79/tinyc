package test.package2;

abstract class Messager<T extends CharSequence> {
    protected abstract void printMessage(T val1, T val2);
}
