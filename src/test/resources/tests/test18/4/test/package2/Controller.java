package test.package2;

public class Controller extends Messager {
    @Override
    public <T> void printMessage(T val1, T val2) {
        System.out.println(String.join(", ", val1.toString(), val2.toString()));
    }
}
