package dk.lessismore.nojpa.masterworker.master;

import dk.lessismore.nojpa.masterworker.JobStatus;
import dk.lessismore.nojpa.masterworker.messages.JobProgressMessage;
import dk.lessismore.nojpa.masterworker.messages.JobResultMessage;
import dk.lessismore.nojpa.masterworker.messages.JobStatusMessage;
import dk.lessismore.nojpa.masterworker.messages.RunMethodRemoteResultMessage;
import dk.lessismore.nojpa.net.link.ServerLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class MessageSender {

    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);

    public static void sendStatusToClient(String jobID, JobStatus jobStatus, ServerLink client, FailureHandler failureHandler) {
        log.debug("sendStatusToClient("+ jobID +")");
        JobStatusMessage statusMessage = new JobStatusMessage(jobID, jobStatus);
        send(statusMessage, client, failureHandler);
    }

    public static void sendProgressToClient(String jobID, double progress, ServerLink client, FailureHandler failureHandler) {
        log.debug("sendProgressToClient("+ jobID +")");
        JobProgressMessage progressMessage = new JobProgressMessage(jobID, progress);
        send(progressMessage, client, failureHandler);
    }

    public static void sendResultToClient(JobResultMessage result, ServerLink client, FailureHandler failureHandler) {
        log.debug("sendResultToClient("+ result.getJobID() +")");
        send(result, client, failureHandler);
    }

    public static void sendRunMethodRemoteResultOfToClient(RunMethodRemoteResultMessage runMethodRemoteResultMessage, ServerLink client, FailureHandler failureHandler) {
        log.debug("sendRunMethodRemoteResultOfToClient: sending " + runMethodRemoteResultMessage);
        send(runMethodRemoteResultMessage, client, failureHandler);
    }
    /**
     * Send message asynchronous.
     * @param message object to send
     * @param client serverLink to recipient
     * @param failureHandler failure callback or null.
     */
    public static void send(final Object message, final ServerLink client, final FailureHandler failureHandler) {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    client.write(message);
                } catch (IOException e) {
                    if (failureHandler != null) failureHandler.onFailure(client);
                }
            }
        });
        thread.setDaemon(true);
        thread.run();
    }

    public static void sendOrTimeout(final Object message, final ServerLink client, final FailureHandler failureHandler) {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    client.writeWithTimeout(message, 1000);
                } catch (IOException e) {
                    log.debug(e.getClass().getSimpleName() + " while sending "+message.getClass().getSimpleName()+" to client");
                    if (failureHandler != null) failureHandler.onFailure(client);
                }
            }
        });
        thread.setDaemon(true);
        thread.run();
    }

    public interface FailureHandler {
        void onFailure(ServerLink client);
    }
}
