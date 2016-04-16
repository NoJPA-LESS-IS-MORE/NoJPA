package dk.lessismore.nojpa.masterworker.master;

import dk.lessismore.nojpa.masterworker.exceptions.JobDoesNotExistException;
import dk.lessismore.nojpa.masterworker.messages.*;
import dk.lessismore.nojpa.masterworker.messages.observer.UpdateMessage;
import dk.lessismore.nojpa.net.link.ServerLink;
import dk.lessismore.nojpa.properties.PropertiesListener;
import dk.lessismore.nojpa.properties.PropertiesProxy;
import dk.lessismore.nojpa.serialization.Serializer;
import dk.lessismore.nojpa.serialization.XmlSerializer;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;


public class MasterServer {

    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(MasterServer.class);
    private static final MasterProperties properties = PropertiesProxy.getInstance(MasterProperties.class);
    static {
        properties.addListener(new PropertiesListener() {
            public void onChanged() {
                log.info("Master.properties changed");
            }
        });
    }
    private JobPool jobPool = new JobPool();
    private WorkerPool workerPool = new WorkerPool();
    private HashSet<ServerLink> observers = new HashSet<ServerLink>();
    private final Serializer storeSerializer = new XmlSerializer();
    private final Calendar started = Calendar.getInstance();


    public void runMethodRemote(RunMethodRemoteBeanMessage runMethodRemoteBeanMessage, ServerLink serverLink) throws IOException {
        jobPool.runMethodRemote(runMethodRemoteBeanMessage, serverLink);
        notifyObservers();
    }


    // Client services
    synchronized void startListen(String jobID, ServerLink client) {
        if (jobID == null) log.error("Can't listen to null job");
        else {
            boolean listenerAdded = jobPool.addListener(jobID, client);
            // No job entry in pool, look for stored results.
            if (! listenerAdded) {
                log.debug("Trying to find stored job result");
                JobResultMessage jobResultMessage = restoreResult(jobID);
                if (jobResultMessage == null) {
                    log.debug("No stored result found, sending back exception");
                    jobResultMessage = new JobResultMessage(jobID);
                    jobResultMessage.setMasterException(new JobDoesNotExistException());
                }
                MessageSender.sendResultToClient(jobResultMessage, client, new MessageSender.FailureHandler() {
                    public void onFailure(ServerLink client) {
                        log.warn("Failed to send restored result to client");
                    }
                });
            }
        }
        notifyObservers();
    }

    synchronized void stopListen(ServerLink client) {
        log.trace("stopListen: " + client);
        jobPool.removeListener(client);
        notifyObservers();
    }

    synchronized public void queueJob(JobMessage jobMessage) {
        log.trace("queueJob: " + jobMessage);
        jobPool.addJob(jobMessage);
        runJobIfNecessaryAndPossible();
        notifyObservers();
    }


    // Worker services
    synchronized public void registerWorker(String[] knownClasses, ServerLink serverLink) {
        log.trace("registerWorker: " + serverLink);
        workerPool.addWorker(knownClasses, serverLink);
        runJobIfNecessaryAndPossible();
        notifyObservers();
    }

    synchronized public void unregisterWorker(ServerLink serverLink) {
        log.trace("unregisterWorker: " + serverLink);
        jobPool.requeueJobIfRunning(serverLink);
        workerPool.removeWorker(serverLink);
        notifyObservers();
    }

    synchronized public void updateWorkerHealth(HealthMessage healthMessage, ServerLink serverLink) {
        log.trace("updateWorkerHealth: " + serverLink);
        boolean applicableBefore = workerPool.applicable(serverLink);
        workerPool.updateWorkerHealth(healthMessage.getSystemLoad(), healthMessage.getVmMemoryUsage(),
                healthMessage.getDiskUsages(), serverLink);
        if (!applicableBefore) {
            boolean applicableAfter = workerPool.applicable(serverLink);
            if (applicableAfter) runJobIfNecessaryAndPossible();
        }
        //notifyObservers();
    }

    synchronized public void updateJobProgress(JobProgressMessage jobProgressMessage) {
        log.trace("updateJobProgress: " + jobProgressMessage);
        jobPool.updateJobProgress(jobProgressMessage.getJobID(), jobProgressMessage.getProgress());
        notifyObservers();
    }



    synchronized public void setRunMethodRemoteResultMessage(RunMethodRemoteResultMessage runMethodRemoteResultMessage) {
        //storeResult(result); TODO
        log.trace("setRunMethodRemoteResultMessage: " + runMethodRemoteResultMessage);
        jobPool.setRunMethodRemoteResultMessage(runMethodRemoteResultMessage);
        notifyObservers();
    }

    synchronized public void setResult(JobResultMessage result, ServerLink serverLink) {
        log.trace("setResult: " + serverLink);
        storeResult(result);
        jobPool.setResult(result);
        workerPool.setIdle(true, serverLink);
        runJobIfNecessaryAndPossible();
    }


    // Observer services
    public void registerObserver(ServerLink serverLink) {
        log.trace("registerObserver: " + serverLink);
        addObserver(serverLink);
        //notifyObservers();
    }



    // Local stuff
    private void addObserver(ServerLink serverLink) {
        log.trace("addObserver: " + serverLink);
        observers.add(serverLink);
    }

    private void removeObserver(ServerLink serverLink) {
        log.trace("removeObserver: " + serverLink);
        observers.remove(serverLink);
    }


    long lastUpdate = System.currentTimeMillis();
    void notifyObservers() {
        log.trace("notifyObservers: ");
        long now = System.currentTimeMillis();
        if(now - lastUpdate < 1000 * 1){
            return;
        }
        lastUpdate = now;
        if (observers == null || observers.isEmpty()) return;
        UpdateMessage updateMessage = new UpdateMessage();
        updateMessage.setObserverJobMessages(jobPool.getObserverJobMessageList());
        updateMessage.setObserverWorkerMessages(workerPool.getObserverWorkerMessageList());
        updateMessage.setStarted(started);
        final List<ServerLink> deadObservers = new ArrayList<ServerLink>();
        for (final ServerLink observer: (Set<ServerLink>) observers.clone()) {
            MessageSender.sendOrTimeout(updateMessage, observer, new MessageSender.FailureHandler() {
                public void onFailure(ServerLink client) {
                    log.warn("Failed to send message to observer "+ observer.getOtherHostPort() + " - removing observer");
                    deadObservers.add(observer);
                }
            });
        }
        if (!deadObservers.isEmpty()) {
            for (ServerLink deadObserver: deadObservers) {
                removeObserver(deadObserver);
            }
            deadObservers.clear();
        }
    }

    public void acceptClientConnection(ServerSocket serverSocket) {
        log.trace("acceptClientConnection: " + serverSocket);
        ServerLink serverLink = acceptConnection(serverSocket);
        if (serverLink != null) {
            new MasterClientThread(this, serverLink).start();
        }
        notifyObservers();
    }

    public void acceptWorkerConnection(ServerSocket serverSocket) {
        log.trace("acceptWorkerConnection: " + serverSocket);
        ServerLink serverLink = acceptConnection(serverSocket);
        if (serverLink != null) {
            new MasterWorkerThread(this, serverLink).start();
        }
        notifyObservers();
    }

    public void acceptObserverConnection(ServerSocket serverSocket) {
        log.trace("acceptObserverConnection: " + serverSocket);
        ServerLink serverLink = acceptConnection(serverSocket);
        if (serverLink != null) {
            new MasterObserverThread(this, serverLink).start();
        }
    }

    private ServerLink acceptConnection(ServerSocket serverSocket) {
        log.trace("acceptConnection: " + serverSocket);
        try {
            Socket socket;
            socket = serverSocket.accept();
            socket.setKeepAlive(true);
            socket.setSoTimeout(1000 * 180);
            socket.setTcpNoDelay(true);
            return new ServerLink(socket);
        } catch (IOException e) {
            log.warn("Socket could not be accepted", e);
            return null;
        }
    }

    private File getStoredResultFile(String jobID) {
        log.trace("getStoredResultFile: " + jobID);
        String resultDirName = properties.getStoreResultDir();
        File resultDir = new File(resultDirName);
        if (! resultDir.isDirectory()) {
            boolean success = resultDir.mkdirs();
            if (! success) {
                log.error("Failed to create directory to store results at "+ resultDirName);
                return null;
            }
        }
        String resultFileName = jobID + ".xml";
        return new File(resultDir, resultFileName);
    }

    private void storeResult(JobResultMessage result) {
        log.trace("storeResult: " + result);
        File resultFile = getStoredResultFile(result.getJobID());
        if (resultFile == null) return;
        try {
            storeSerializer.store(result, resultFile);
            log.debug("Result saved to file " + resultFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed save result to file " + resultFile.getAbsolutePath(), e);
        }
    }

    private JobResultMessage restoreResult(String jobID) {
        log.trace("restoreResult: " + jobID);
        File resultFile = getStoredResultFile(jobID);
        if (resultFile == null) return null;
        try {
            return (JobResultMessage) storeSerializer.restore(resultFile);
        } catch (IOException e) {
            log.error("Error while trying to load stored result");
            return null;
        }
    }

    synchronized private void runJobIfNecessaryAndPossible() {
        log.trace("runJobIfNecessaryAndPossible");
        System.out.println(jobPool.toString() + workerPool.toString());
        final JobPool.JobEntry jobEntry = jobPool.firstJob();
        if (jobEntry == null) {
            log.debug("No Job in queue to run");
            return;
        }

        final WorkerPool.WorkerEntry workerEntry = workerPool.getBestApplicableWorker(
                jobEntry.jobMessage.getExecutorClassName());
        if (workerEntry == null) {
            log.debug("No available worker to run job");
            return;
        }
        log.debug("Fond worker to run job: "+ workerEntry);
        jobPool.jobTaken(jobEntry, workerEntry.serverLink);


        MessageSender.send(jobEntry.jobMessage, workerEntry.serverLink, new MessageSender.FailureHandler() {
            public void onFailure(ServerLink client) {
                log.debug("IOException while sending job to worker - removing worker");
                MasterServer.this.unregisterWorker(workerEntry.serverLink);
            }
        });
        notifyObservers();
    }

    public void restartAllWorkers() {
        log.trace("restartAllWorkers");
        Map.Entry<ServerLink, WorkerPool.WorkerEntry>[] entries = workerPool.pool.entrySet().toArray(new Map.Entry[workerPool.pool.size()]);
        for(int i = 0; i < entries.length; i++){
            log.debug("restartAllWorkers("+ i +"/"+ entries.length +")");
            Map.Entry<ServerLink, WorkerPool.WorkerEntry> entry = entries[i];
            try {
                entry.getKey().stopPinger();
                entry.getKey().write(new KillMessage());
            } catch (IOException e) {
                log.warn("When restartAllWorkers we got from worker("+ entry.getValue().toString() +")  : "+ e, e);
            }
        }

    }
}