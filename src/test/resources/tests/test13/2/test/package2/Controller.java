package test.package2;

import java.io.Serializable;

public class Controller extends Messager2 {
    @Override
    public void printMessage(String val1, String val2) {
        System.out.println(String.join(" ", val1, val2));
    }
}

abstract class Messager implements Serializable {
    protected abstract void printMessage(String val1, String val2);
}
