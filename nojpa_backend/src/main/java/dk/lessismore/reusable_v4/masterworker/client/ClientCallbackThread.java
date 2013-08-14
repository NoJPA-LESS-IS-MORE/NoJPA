package dk.lessismore.reusable_v4.masterworker.client;

import dk.lessismore.reusable_v4.net.link.ClientLink;
import dk.lessismore.reusable_v4.serialization.Serializer;
import dk.lessismore.reusable_v4.masterworker.messages.*;
import dk.lessismore.reusable_v4.masterworker.JobStatus;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;


public class ClientCallbackThread<O> extends Thread {

    private static Logger log = org.apache.log4j.Logger.getLogger(ClientCallbackThread.class);
    private final ClientLink cp;
    private final JobHandleToMasterProtocol<O> jm;
    private final Serializer serializer;

    public ClientCallbackThread(JobHandleToMasterProtocol<O> jm, ClientLink clientLink, Serializer serializer) {
        this.cp = clientLink;
        this.jm = jm;
        this.serializer = serializer;
    }


    public void run() {
        try{
            while(true) {
                Object message = cp.read();
                log.debug("Message recieved from Master '" + message.getClass().getSimpleName() + "' " + message);

                if(message instanceof PingMessage) {
                    cp.write(new PongMessage());
                } else if(message instanceof RunMethodRemoteResultMessage) {
                    RunMethodRemoteResultMessage runMethodRemoteResultMessage = (RunMethodRemoteResultMessage) message;
                    jm.notifyRunMethodRemoteResult( runMethodRemoteResultMessage );
                } else if(message instanceof JobResultMessage) {
                    JobResultMessage<O> jobResultMessage = (JobResultMessage<O>) message;
                    jm.notifyStatus(JobStatus.DONE);
                    if (jobResultMessage.hasException()) {
                        jm.notifyException(jobResultMessage.getException(serializer));
                    } else if (jobResultMessage.hasMasterException()) {
                        jm.notifyException(jobResultMessage.getMasterException());
                    } else {
                        jm.notifyResult(jobResultMessage.getResult(serializer));
                    }
                } else if(message instanceof JobStatusMessage) {
                    JobStatusMessage jobStatusMessage = (JobStatusMessage) message;
                    jm.notifyStatus(jobStatusMessage.getStatus());
                } else if(message instanceof JobProgressMessage) {
                    JobProgressMessage jobProgressMessage = (JobProgressMessage) message;
                    jm.notifyProgress(jobProgressMessage.getProgress());
                } else {
                    System.out.println("Don't know message = " + message);
                }
            }
        } catch (ClosedChannelException e) {
            log.info("Connection closed - stopping listening for callbacks from master");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}