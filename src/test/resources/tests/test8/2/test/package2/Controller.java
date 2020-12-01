package test.package2;

public class Controller {
    public CharSequence val1;
    public CharSequence val2;
    public void run() {
        System.out.println(String.join(", ", val1, val2));
    }
}
