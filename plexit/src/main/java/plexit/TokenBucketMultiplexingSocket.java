package plexit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Semaphore;

class TokenBucketMultiplexingSocket extends Socket {
    private final TokenBucket bucket;
    private final Semaphore semaphore = new Semaphore(0);

    public TokenBucketMultiplexingSocket(String host, int port, int rate) throws IOException {
        super(host, port);
        this.bucket = new TokenBucket(rate);

        Thread thread = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    if (bucket.acquire()) {
                        semaphore.release();
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return new TokenBucketOutputStream(super.getOutputStream(), semaphore);
    }
}

