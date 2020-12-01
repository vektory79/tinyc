package test.package2;

public class Controller {
    public void printMessage(@Metadata String val1, @Metadata String val2) {
        System.out.println(String.join(", ", val1, val2));
    }
}
