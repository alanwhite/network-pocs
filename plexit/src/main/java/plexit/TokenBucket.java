package plexit;

class TokenBucket {
    private final int rate;
    private int tokens;

    public TokenBucket(int rate) {
        this.rate = rate;
        this.tokens = rate;
    }

    public synchronized boolean acquire() {
        if (tokens > 0) {
            tokens--;
            return true;
        }
        return false;
    }

    public synchronized void release() {
        tokens++;
    }

    public synchronized void addTokens() {
        tokens += rate;
    }
}

