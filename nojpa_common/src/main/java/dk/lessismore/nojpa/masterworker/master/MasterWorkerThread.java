package dk.lessismore.nojpa.masterworker.master;

import dk.lessismore.nojpa.masterworker.messages.HealthMessage;
import dk.lessismore.nojpa.masterworker.messages.JobProgressMessage;
import dk.lessismore.nojpa.masterworker.messages.JobResultMessage;
import dk.lessismore.nojpa.masterworker.messages.PingMessage;
import dk.lessismore.nojpa.masterworker.messages.PongMessage;
import dk.lessismore.nojpa.masterworker.messages.RegistrationMessage;
import dk.lessismore.nojpa.masterworker.messages.RunMethodRemoteResultMessage;
import dk.lessismore.nojpa.net.link.ServerLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;


public class MasterWorkerThread implements Runnable{

    private static final Logger log = LoggerFactory.getLogger(MasterWorkerThread.class);
    private final ServerLink serverLink;
    private final MasterServer masterServer;

    public MasterWorkerThread(MasterServer masterServer, ServerLink serverLink) {
        this.serverLink = serverLink;
        this.masterServer = masterServer;
    }

    public void run() {
        MDC.put("workerID", serverLink.getLinkID());
        try{
            while(true) {
                Object clientRequest = serverLink.read();
                if(!clientRequest.getClass().getSimpleName().equals("HealthMessage") && !clientRequest.getClass().getSimpleName().equals("PingMessage") && !clientRequest.getClass().getSimpleName().equals("PongMessage")){
                    log.debug(serverLink.getLinkID() + " Got clientRequest " + clientRequest.getClass().getSimpleName());
                }
                if (! (clientRequest instanceof HealthMessage) &&
                    ! (clientRequest instanceof JobProgressMessage) &&
                    ! (clientRequest instanceof PongMessage))
                    log.debug("Message recieved from worker '" + clientRequest.getClass().getSimpleName() + "'");
                if(clientRequest instanceof PingMessage) {
                    serverLink.write(new PongMessage());
                } else if(clientRequest instanceof RegistrationMessage) {
                    RegistrationMessage registrationMessage = (RegistrationMessage) clientRequest;
                    masterServer.registerWorker(registrationMessage, serverLink);
                } else if(clientRequest instanceof HealthMessage) {
                    HealthMessage healthMessage = (HealthMessage) clientRequest;
                    masterServer.updateWorkerHealth(healthMessage, serverLink);
                } else if(clientRequest instanceof JobProgressMessage) {
                    JobProgressMessage jobProgressMessage = (JobProgressMessage) clientRequest;
                    masterServer.updateJobProgress(jobProgressMessage);
                } else if(clientRequest instanceof PongMessage) {
                } else if(clientRequest instanceof RunMethodRemoteResultMessage) {
                    RunMethodRemoteResultMessage runMethodRemoteResultMessage = (RunMethodRemoteResultMessage) clientRequest;
                    masterServer.setRunMethodRemoteResultMessage(runMethodRemoteResultMessage);
                } else if(clientRequest instanceof JobResultMessage) {
                    JobResultMessage jobResultMessage = (JobResultMessage) clientRequest;
                    masterServer.setResult(jobResultMessage, serverLink);
                } else {
                    log.warn("Don't know message from worker = " + clientRequest);
                }
            }
        } catch (ClosedChannelException e) {
            log.info("Connection closed - stopping listening to worker("+ serverLink.getLinkID() +")");
        } catch (IOException e) {
            log.error("IOException - stopping listening to worker("+ serverLink.getLinkID() +"):", e);
        } finally {
            try{
                serverLink.close();
            } catch (Exception e){}
            masterServer.unregisterWorker(serverLink);
        }
    }
}
