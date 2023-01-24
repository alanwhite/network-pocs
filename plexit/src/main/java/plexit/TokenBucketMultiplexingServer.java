package plexit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

class TokenBucketMultiplexingServer extends MultiplexingServer {
    private final Map<Integer, TokenBucket> buckets = new HashMap<>();

    public TokenBucketMultiplexingServer(int port, int rate) throws IOException {
        super(port);
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                synchronized (buckets) {
                    for (TokenBucket bucket : buckets.values()) {
                        bucket.addTokens();
                    }
                }
            }
        }, 0, 1000);
    }

    public void run() {
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                int id = socket.hashCode();
                buckets.put(id, new TokenBucket(rate));
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            InputStream in = socket.getInputStream();
                            byte[] buffer =
                                    while (true) {
                                        int read = in.read(buffer);
                                        if (read == -1) {
                                            break;
                                        }
                                        String message = new String(buffer, 0, read);
                                        String[] parts = message.split(":", 3);
                                        int id = Integer.parseInt(parts[0]);
                                        String command = parts[1];
                                        TokenBucket bucket = buckets.get(id);
                                        if (command.equals("CONNECT")) {
                                            String[] endpointParts = parts[2].split(":", 2);
                                            String host = endpointParts[0];
                                            int port = Integer.parseInt(endpointParts[1]);
                                            int timeout = Integer.parseInt(parts[3]);
                                            Socket multiplexedSocket = new Socket(host, port);
                                            multiplexedSocket.connect(new InetSocketAddress(host, port), timeout);
                                            sockets.put(id, multiplexedSocket);
                                            socket.getOutputStream().write("OK".getBytes());
                                        } else if (command.equals("CLOSE")) {
                                            sockets.get(id).close();
                                        } else if (command.equals("DATA")) {
                                            if (bucket.acquire()) {
                                                Socket multiplexedSocket = sockets.get(id);
                                                OutputStream out = multiplexedSocket.getOutputStream();
                                                out.write(parts[2].getBytes());
                                                InputStream in2 = multiplexedSocket.getInputStream();
                                                int read2 = in2.read(buffer);
                                                if (read2 != -1) {
                                                    String response = new String(buffer, 0, read2);
                                                    socket.getOutputStream().write(response.getBytes());
                                                }
                                                bucket.release();
                                            } else {
                                                socket.getOutputStream().write("BUCKET_FULL".getBytes());
                                            }
                                        }
                                    }
                                    socket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                        thread.start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

