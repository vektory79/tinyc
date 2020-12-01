package test.package2;

public class Controller implements Messager {
    @Override
    public void printMessage(String val1, String val2) {
        System.out.println(String.join(", ", val1, val2));
    }
}
