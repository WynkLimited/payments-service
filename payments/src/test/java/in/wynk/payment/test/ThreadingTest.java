package in.wynk.payment.test;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadingTest {

    @Test
    public void testCountDownLatch() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch countDownLatch = new CountDownLatch(5);
        for(int i =0; i<5;i++){
            executorService.submit(new TestRunnable(countDownLatch, 10, 20));
        }
        countDownLatch.await(1, TimeUnit.SECONDS);
        System.out.println(Thread.currentThread().getName() +" AWAITED COUNT " + countDownLatch.getCount());
    }

    private static class TestRunnable implements Runnable{
        private CountDownLatch latch;
        private int i;
        private int j;


        private TestRunnable(CountDownLatch latch, int i, int j){
            this.i = i;
            this.j = j;
            this.latch = latch;
        }
        @Override
        public void run() {
            for(int x = 0; x<=2;x++){
                try {
                    System.out.println(Thread.currentThread().getName() + " Sleeping.....");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println(Thread.currentThread().getName() + " COUNT " + latch.getCount());
            latch.countDown();
            System.out.println(Thread.currentThread().getName() + " " + i +" " + j);
        }
    }
}
