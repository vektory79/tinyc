package test.package2;

public class Controller<T> extends Messager<T> {
    @Override
    public void printMessage(T val1, T val2) {
        System.out.println(String.join(", ", val1.toString(), val2.toString()));
    }
}
