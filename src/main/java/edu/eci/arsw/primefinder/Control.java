package edu.eci.arsw.primefinder;

import java.util.Scanner;

public class Control extends Thread {
    
    private final static int NTHREADS = 3;
    private final static int MAXVALUE = 30000000;
    private final static int TMILISECONDS = 5000;
    private final int NDATA = MAXVALUE / NTHREADS;
    
    private PrimeFinderThread pft[];
    
    private boolean isPaused = false;

    private Control() {
        super();
        this.pft = new PrimeFinderThread[NTHREADS];

        int i;
        for (i = 0; i < NTHREADS - 1; i++) {
            PrimeFinderThread elem = new PrimeFinderThread(i * NDATA, (i + 1) * NDATA, this);
            pft[i] = elem;
        }
        pft[i] = new PrimeFinderThread(i * NDATA, MAXVALUE + 1, this);
    }
    
    public static Control newControl() {
        return new Control();
    }

    public synchronized void checkPause() throws InterruptedException {
        while (isPaused) {
            this.wait(); 
        }
    }

    @Override
    public void run() {
        for (int i = 0; i < NTHREADS; i++) {
            pft[i].start();
        }

        Scanner scanner = new Scanner(System.in);
        boolean threadsAlive = true;

        while (threadsAlive) {
            try {
                
                Thread.sleep(TMILISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            synchronized (this) {
                isPaused = true;
            }

            int totalPrimes = 0;
            for (PrimeFinderThread thread : pft) {
                totalPrimes += thread.getPrimes().size();
            }

            System.out.println("\n--- PAUSA ---");
            System.out.println("Primos encontrados hasta ahora: " + totalPrimes);
            System.out.println("Presiona ENTER para reanudar...");

            scanner.nextLine(); 

            synchronized (this) {
                isPaused = false;
                this.notifyAll(); 
            }

            threadsAlive = false;
            for (PrimeFinderThread thread : pft) {
                if (thread.isAlive()) {
                    threadsAlive = true;
                    break;
                }
            }
        }
        scanner.close();
        System.out.println("Búsqueda finalizada.");
    }
}