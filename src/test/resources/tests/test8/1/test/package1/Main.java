package test.package1;

import test.package2.Controller;

public class Main {
    public static void main(String[] args) {
        Controller controller = new Controller();
        controller.val1 = "Hello";
        controller.val2 = "world";
        controller.run();
    }
}
