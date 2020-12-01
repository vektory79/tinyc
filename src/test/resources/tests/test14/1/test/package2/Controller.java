package test.package2;

public class Controller extends Messager2 {
    @Override
    public void printMessage(String val1, String val2) {
        System.out.println(String.join(", ", val1, val2));
    }
}

abstract class Messager {
    protected abstract void printMessage(String val1, String val2);
}
