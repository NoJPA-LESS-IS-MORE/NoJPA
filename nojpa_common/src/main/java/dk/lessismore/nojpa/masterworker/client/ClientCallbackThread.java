package dk.lessismore.nojpa.masterworker.client;

import dk.lessismore.nojpa.masterworker.JobStatus;
import dk.lessismore.nojpa.masterworker.messages.JobProgressMessage;
import dk.lessismore.nojpa.masterworker.messages.JobResultMessage;
import dk.lessismore.nojpa.masterworker.messages.JobStatusMessage;
import dk.lessismore.nojpa.masterworker.messages.PingMessage;
import dk.lessismore.nojpa.masterworker.messages.PongMessage;
import dk.lessismore.nojpa.masterworker.messages.RunMethodRemoteResultMessage;
import dk.lessismore.nojpa.net.link.ClientLink;
import dk.lessismore.nojpa.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;


public class ClientCallbackThread<O> extends Thread {

    private static final Logger log = LoggerFactory.getLogger(ClientCallbackThread.class);
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
                log.debug("Message recieved from Master '" + message.getClass().getSimpleName() + "' ");

                if(message instanceof PingMessage) {
                    cp.write(new PongMessage());
                } else if(message instanceof RunMethodRemoteResultMessage) {
                    RunMethodRemoteResultMessage runMethodRemoteResultMessage = (RunMethodRemoteResultMessage) message;
                    jm.notifyRunMethodRemoteResult( runMethodRemoteResultMessage );
                } else if(message instanceof JobResultMessage) {
                    JobResultMessage<O> jobResultMessage = (JobResultMessage<O>) message;
                    log.debug("Message is JobResultMessage ("+ jobResultMessage.getJobID() +") with workerID("+ jobResultMessage.getWorkerID() +")");
                    if(jm.matchJobID(jobResultMessage.getJobID())){
                        jm.notifyStatus(JobStatus.DONE);
                        if (jobResultMessage.hasException()) {
                            jm.notifyException(jobResultMessage.getException(serializer));
                        } else if (jobResultMessage.hasMasterException()) {
                            jm.notifyException(jobResultMessage.getMasterException());
                        } else {
                            jm.notifyResult(jobResultMessage.getResult(serializer));
                        }
                    } else {
                        log.info("JobID dont matches JobResultMessage("+ jobResultMessage.getJobID() +") ");
                    }
                } else if(message instanceof JobStatusMessage) {
                    JobStatusMessage jobStatusMessage = (JobStatusMessage) message;
                    log.debug("Message is JobStatusMessage ("+ jobStatusMessage.getJobID() +") with workerID("+ ((JobStatusMessage) message).getWorkerID() +")");
                    if(jm.matchJobID(jobStatusMessage.getJobID())){
                        jm.notifyStatus(jobStatusMessage.getStatus());
                    } else {
                        log.info("JobID dont matches JobStatusMessage("+ jobStatusMessage.getJobID() +") ");
                    }
                } else if(message instanceof JobProgressMessage) {
                    JobProgressMessage jobProgressMessage = (JobProgressMessage) message;
                    if(jm.matchJobID(jobProgressMessage.getJobID())){
                        log.debug("Message is jobProgressMessage ("+ jobProgressMessage.getJobID() +")");
                        jm.notifyProgress(jobProgressMessage.getProgress());
                    } else {
                        log.info("JobID dont matches JobProgressMessage("+ jobProgressMessage.getJobID() +") ");
                    }
                } else {
                    log.error("Don't know message = " + message);
                }
            }
        } catch (ClosedChannelException e) {
            log.info("Connection closed - stopping listening for callbacks from master: " + e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                jm.close();
            } catch (Exception e){

            }
            log.debug("Ending runner");
        }

    }
}