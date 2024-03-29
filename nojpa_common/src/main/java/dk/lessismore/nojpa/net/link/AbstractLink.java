package dk.lessismore.nojpa.net.link;


import dk.lessismore.nojpa.guid.GuidFactory;
import dk.lessismore.nojpa.masterworker.executor.Executor;
import dk.lessismore.nojpa.serialization.Serializer;
import dk.lessismore.nojpa.serialization.XmlSerializer;
import dk.lessismore.nojpa.utils.SuperIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.LinkedList;

public abstract class AbstractLink {

    private static final Logger log = LoggerFactory.getLogger(AbstractLink.class);

    private static final boolean DEBUG_STACK_TRACE = false;
    protected Socket socket = null;
    protected OutputStream out = null;
    protected InputStream in = null;
    private final LinkedList<Object> receivedObjects = new LinkedList<Object>();
    private final Serializer serializer;
    private static final String SEPARATOR = "<~>";

    private String linkID = GuidFactory.getInstance().makeGuid();

    private long totalReadBytes = 0;
    private long totalWriteBytes = 0;

    private boolean hasErrors = false;

    public boolean isWorking(){
        return socket != null && socket.isConnected() && out != null && in != null && !hasErrors;
    }




    protected AbstractLink(Serializer serializer) {
        if (serializer == null) {
            this.serializer = new XmlSerializer();
        } else {
            this.serializer = serializer;
        }
        if(DEBUG_STACK_TRACE) {
            log.debug("Starting for link(" + getLinkID() + ")", new RuntimeException("DEBUG-STACK-TRACE"));
        } else {
            log.debug("Starting for link("+ getLinkID() +")");
        }

    }

    protected AbstractLink() {
        this(null);
    }


    public String getLinkID() {
        return linkID;
    }

    public void setLinkID(String jobIDOrLinkID) {
        linkID = jobIDOrLinkID;
    }

    /**
     * Send object to receiver.
     * @param o Object to send.
     * @throws IOException Connection error.
     */
    public synchronized void write(Object o) throws IOException {
        try {
            String serializedObject = serializer.serialize(o);
            totalWriteBytes += serializedObject.length();
//        if(!o.getClass().equals(Ping.class)){
//            log.debug("Writing: " + o + " totalReadBytes("+ totalReadBytes +") totalWriteBytes("+ totalWriteBytes +")");
//        }
            if(out == null){
                throw new RuntimeException("Writing on a closed connection... " + getLinkID());
            } else {
                out.write((serializedObject + SEPARATOR).getBytes());
                out.flush();
            }
        } catch (IOException e){
            log.error("ERROR when writing to ServerLink("+ getLinkID() +") " + e);
            throw e;
        }
    }

    /**
     * Send object to receiver.
     * A WriteTimeoutException is thrown of the message is not sent within the given time.
     * @param o Object to send.
     * @param timeout time in milliseconds
     * @throws WriteTimeoutException Timeout exceeded.
     * @throws IOException Some socket error
     */
    public void writeWithTimeout(Object o, final int timeout) throws IOException {
        final String serializedObject = serializer.serialize(o);
        final Boolean[] writeCompleted = new Boolean[1];
        final IOException[] writeException = new IOException[1];

        final Thread timeoutThread = new Thread(new Runnable() {
            IOException e = null;
            public void run() {
                try {
                    Thread.sleep(timeout);
                    try {
                        out.close();
                    } catch (IOException e1) {
                        log.error("Failed to close socket", e1);
                    }
                } catch (InterruptedException e1) {
                    // Thread interrupted on purpose !
                }
            }
        });

        Thread writeThread = new Thread(new Runnable() {
            public void run() {
                try {
                    out.write((serializedObject + SEPARATOR).getBytes());
                    out.flush();
                    writeCompleted[0] = true;
                    timeoutThread.interrupt();
                } catch (SocketException e) {
                    if (e.getMessage().equalsIgnoreCase("Socket closed")) {
                        writeCompleted[0] = false;
                    } else {
                        writeException[0] = e;
                    }
                } catch (IOException e) {
                    writeException[0] = e;
                }
            }
        });

        timeoutThread.start();
        writeThread.start();
        try {
            timeoutThread.join();
            writeThread.join();
        } catch (InterruptedException e) {
            log.error(getLinkID()+":Failed to join threads", e);
        }

        if (writeException[0] != null) {
            throw writeException[0];
        } else if ( ! writeCompleted[0]) {
            throw new WriteTimeoutException(getLinkID() + ":Time on "+timeout+"ms exceeded while writing on link");
        }
    }


    /**
     * Blocks until an object are received.
     * @return Received object.
     * @throws ClosedChannelException Connection closed.
     * @throws IOException Connection error.
     */
    public Object read() throws IOException {
        synchronized (receivedObjects) {
            if (! receivedObjects.isEmpty()) {
                Object recivedObject = receivedObjects.getFirst();
                receivedObjects.removeFirst();
//                log.debug("Reading: " + recivedObject);
                return recivedObject;
            } else {
                byte[] buffer = new byte[16 * 1024];
                StringBuilder builder = new StringBuilder();
                int length = 0;
                String end = null;
                while (end == null || length < 3 || !end.equals(SEPARATOR)){
                    int byteLength = 0;
                    try {
                        byteLength = in.read(buffer);
                        totalReadBytes += byteLength;
                    } catch (Exception e) {
                        throw new ClosedChannelException();
                    }
                    if (byteLength == -1) {
                        throw new ClosedChannelException();
                    } else {
                        String s = new String(buffer, 0, byteLength);
                        builder.append(s);
                    }
                    length = builder.length();
                    end = builder.substring(length - SEPARATOR.length());
                }
                String s = builder.toString();
                int startIndex = 0;
                while (startIndex != -1) {
                    int endIndex = s.indexOf(SEPARATOR, startIndex);
                    if (endIndex != -1) {
                        String objectStr = s.substring(startIndex, endIndex);
                        try {
//                            try{
//                                String root = "/tmp/mw-all-files/" + getLinkID();
//                                new File(root).mkdirs();
//                                SuperIO.writeTextToFile(root + "/" + System.currentTimeMillis() + "-" + GuidFactory.getInstance().makeGuid(), objectStr);
//                            } catch (Throwable t){
//
//                            }

                            Object o = serializer.unserialize(objectStr);
                            receivedObjects.addLast(o);
                            startIndex = endIndex + SEPARATOR.length();
                        } catch (Exception e) {
                            log.error("An error happen while unserialize... objectStr = " + objectStr);
                            throw new IOException(e);
                        }
                    } else {
                        startIndex = endIndex;
                    }
                }
                return read();
            }
        }
    }

    public String getLocalHost() {
        return socket.getLocalAddress().getHostName();
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public String getLocalHostPort() {
        return "" + getLocalHost() + ":" + getLocalPort();
    }

    public String getOtherHost() {
        return (socket != null && socket.getInetAddress() != null ?  socket.getInetAddress().getHostName() : "DEAD-LINK");
    }

    public int getOtherPort() {
        return (socket != null ? socket.getPort() : -1);
    }

    public String getOtherHostPort() {
        return "" + getOtherHost() + ":" + getOtherPort();
    }

    public synchronized void close() {
        if(DEBUG_STACK_TRACE) {
            log.debug("Closing for link(" + getLinkID() + ")", new RuntimeException("DEBUG-STACK-TRACE"));
        } else {
            log.debug("Closing for link("+ getLinkID() +")");
        }
        try {
            if (in != null){
                try {
                    in.close();
                } catch (Exception e){}
                in = null;
            }
            if (out != null){
                try {
                    out.close();
                } catch (Exception e){}
                out = null;
            }
            if (socket != null){
                try {
                    socket.shutdownInput();
                } catch (Exception ee){}
                try {
                    socket.shutdownOutput();
                } catch (Exception ee){}
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            log.error("We got a IOException when closing down the connection: "+ e, e);
        }
    }


    @Override
    public String toString() {
        return this.getClass().getSimpleName()+" ID("+ getLinkID() +"):";
    }
}
