package co.eci.snake.concurrency;

public class GameController {
    private boolean isPaused = false;
    private int pausedCount = 0;
    private int totalExpected = 0;

    public synchronized void checkPause() throws InterruptedException {
        if (isPaused) {
            pausedCount++;
            if (pausedCount == totalExpected) {
                this.notifyAll(); 
            }
            while (isPaused) {
                this.wait();
            }
            pausedCount--;
        }
    }

    public synchronized void pause(int totalThreads) throws InterruptedException {
        this.totalExpected = totalThreads;
        this.isPaused = true;
        
        while (pausedCount < totalExpected) {
            this.wait(); 
        }
    }

    public synchronized void resume() {
        this.isPaused = false;
        this.notifyAll();
    }
}