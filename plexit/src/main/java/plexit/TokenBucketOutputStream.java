package plexit;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;

class TokenBucketOutputStream extends OutputStream {
    private final OutputStream out;
    private final TokenBucket bucket;
    private final Semaphore semaphore = new Semaphore(0);

    public TokenBucketOutputStream(OutputStream out, TokenBucket bucket) {
        this.out = out;
        this.bucket = bucket;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        out.write(b, off, len);
        bucket.release();
        semaphore.release();
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] { (byte) b }, 0, 1);
    }
}

