package test.package2;

@Metadata
public class Controller {
    public void printMessage(String val1, String val2) {
        System.out.println(String.join(", ", val1, val2));
    }
}
