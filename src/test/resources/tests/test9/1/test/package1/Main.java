package test.package1;

import test.package2.Controller;

public class Main {
    public static void main(String[] args) {
        System.out.println(String.join(", ", Controller.val1, Controller.val2));
    }
}
