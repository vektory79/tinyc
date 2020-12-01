public class Main {
    public static void main(String[] args) {
        var lambda = new Runnable() {
            @Override
            public void run() {
                System.out.println("Hello, world!!!");
            }
        };
        lambda.run();
    }
}
