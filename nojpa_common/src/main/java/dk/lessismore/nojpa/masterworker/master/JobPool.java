package dk.lessismore.nojpa.masterworker.master;

import dk.lessismore.nojpa.masterworker.JobStatus;
import dk.lessismore.nojpa.masterworker.exceptions.WorkerExecutionException;
import dk.lessismore.nojpa.masterworker.messages.*;
import dk.lessismore.nojpa.masterworker.messages.observer.ObserverJobMessage;
import dk.lessismore.nojpa.net.link.ServerLink;
import dk.lessismore.nojpa.properties.PropertiesProxy;
import dk.lessismore.nojpa.utils.MaxSizeArray;
import dk.lessismore.nojpa.utils.SuperIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

public class JobPool {

    private static final Logger log = LoggerFactory.getLogger(JobPool.class);
    private static final MasterProperties properties = PropertiesProxy.getInstance(MasterProperties.class);

    static private int jobEntrySequenceNumberCounter = 0;
    private final Map<ServerLink, String> clientMap = new HashMap<ServerLink, String>();
    private final Map<ServerLink, String> workerMap = new HashMap<ServerLink, String>();
    private final Map<String, JobEntry> pool = new HashMap<String, JobEntry>();
    private final SortedSet<JobEntry> queue = new TreeSet<JobEntry>(new PriorityComparator());
    private final MaxSizeArray<JobEntry> last5SuccesJobs = new MaxSizeArray<JobEntry>(5);
    private final MessageSender.FailureHandler failureHandler = new MessageSender.FailureHandler() {
        public void onFailure(ServerLink client) {
            log.error("Can't send message to client:" + client);
            removeListener(client);
        }
    };


    public void runMethodRemote(RunMethodRemoteBeanMessage runMethodRemoteBeanMessage, ServerLink serverLink) throws IOException {
        log.debug("runMethodRemote:1:runMethodRemoteBeanMessage("+ runMethodRemoteBeanMessage.getJobID() +")/("+ runMethodRemoteBeanMessage.getMethodName() +")");
        JobPool.JobEntry jobEntry = pool.get(runMethodRemoteBeanMessage.getJobID());
        log.debug("runMethodRemote:2:jobEntry("+ jobEntry +")");
        jobEntry.runMethodRemote(runMethodRemoteBeanMessage, serverLink);
    }




    public synchronized void addJob(JobMessage job) {
        if(pool.get(job.getJobID()) == null) {
            log.debug("Job["+ job.getJobID() +"]: START - Added job: ");
            JobEntry jobEntry = new JobEntry(job);
            pool.put(job.getJobID(), jobEntry);
            queue.add(jobEntry);
            log.debug("Job["+ job.getJobID() +"]: END - Added job: " + jobEntry);
        } else {
            log.error("Job["+ job.getJobID() +"]: all ready exists in pool...! ");
        }
    }


    public int getQueueSize(){
        return queue.size();
    }


    /**
     * Add listener for jobID if the corresponding job entry exists in pool
     * @param jobID ID for the job to listen to.
     * @param client the link to the client
     * @return True if the listener could be added False otherwise.
     */
    public synchronized boolean addListener(String jobID, ServerLink client) {
        clientMap.put(client, jobID);
        JobEntry jobEntry = pool.get(jobID);
        if (jobEntry != null) {
            if (jobEntry.clients == null) jobEntry.clients = new HashSet<ServerLink>();
            jobEntry.clients.add(client);
            if (jobEntry.result == null) {
                //MessageSender.sendStatusToClient(jobID, jobEntry.getStatus(), client, failureHandler);
                //MessageSender.sendProgressToClient(jobID, jobEntry.progress, client, failureHandler);
            } else {
                MessageSender.sendResultToClient(jobEntry.result, client, failureHandler);
            }
            return true;
        } else {
            log.debug("No job entry in job pool - can't add listener for jobID("+ jobID +")");
            return false;
        }
    }

    public synchronized void removeListener(ServerLink client) {
        String jobID = clientMap.get(client);
        if (jobID == null) {
            log.info("No jobID("+ jobID +") found for client - cant remove listener. clientMap.size("+ clientMap.size() +") pool.size("+ pool.size() +")");
            return;
        }

        JobEntry jobEntry = pool.get(jobID);
        if (jobEntry == null) {
            log.debug("No jobEntry exist - cant remove listener for jobID("+ jobID +"). clientMap.size("+ clientMap.size() +") pool.size("+ pool.size() +")");
            return;
        }
        if (jobEntry.clients == null) {
            log.debug("No listening clients exist - cant remove listener for jobID("+ jobID +")");
            return;
        }
        jobEntry.clients.remove(client);
        clientMap.remove(client);
    }

    public void updateJobProgress(String jobID, double progress) {
        JobEntry jobEntry = pool.get(jobID);
        if (jobEntry != null) {
            jobEntry.progress = progress;
            fireOnProgress(jobEntry);
        } else {
            log.error("Trying to set progress for job not in pool");
        }
    }

    public void setRunMethodRemoteResultMessage(RunMethodRemoteResultMessage runMethodRemoteResultMessage){
        if (runMethodRemoteResultMessage == null) {
            log.error("runMethodRemoteResultMessage must be a non-null value");
            return;
        }
        String jobID = runMethodRemoteResultMessage.getJobID();
        JobEntry jobEntry = pool.get(jobID);
        if (jobEntry != null) {
            fireOnRunMethodRemoteResult(jobEntry, runMethodRemoteResultMessage);
        } else {
            log.error("Trying to set result for job not in pool");
        }
    }


    Calendar lastResult;
    long resultTotalCounter = 0;
    long resultLast100Time = 0;
    long resultStartTime = System.currentTimeMillis();
    public void setResult(JobResultMessage result) {
        if (result == null) {
            log.error("Result must be a non-null value");
            return;
        }
        lastResult = Calendar.getInstance();
        String jobID = result.getJobID();

        try {
            JobEntry jobEntry = pool.get(jobID);
            log.debug("setResult["+ jobID +"]->jobEntry("+ jobEntry +")");
            if (jobEntry != null) {
                jobEntry.result = result;
                last5SuccesJobs.add(jobEntry);
                if(resultTotalCounter++ % 100 == 0){
                    resultLast100Time = System.currentTimeMillis();
                }
                fireOnResult(jobEntry, result);
            } else {
                log.error("["+ jobID +"]: Trying to set result for job not in pool");
            }
        } catch (Exception e){
            log.error("ERROR when setting result["+ jobID +"]:"+ e, e);
        }
    }

    public JobEntry firstJob() {
        try {
            return queue.first();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public synchronized void jobTaken(JobEntry jobEntry, ServerLink worker) {
        log.debug(" *** JOB TAKEN: "+jobEntry + " by worker/ServerLink("+ worker +")");
        jobEntry.jobTakenDate = Calendar.getInstance();
        setWorker(jobEntry, worker);
        queue.remove(jobEntry);
        fireOnStatus(jobEntry);
    }

    public synchronized void requeueJobIfRunning(ServerLink worker) {
        log.debug("#################################################### requeueJobIfRunning !!!!");
        log.debug("#################################################### requeueJobIfRunning !!!!");
        log.debug("#################################################### requeueJobIfRunning !!!!");
        String jobID = workerMap.get(worker);
        if (jobID == null) return;
        JobEntry entry = pool.get(jobID);
        if (entry == null) return;

        if (! entry.worker.equals(worker)) { //TODO remove test later
            log.error("workerMap and job entry is inconsistent");
            return;
        }
        if (entry.result != null) { //TODO remove test later
            log.error("workerMap and job entry is inconsistent - entry has result");
            return;
        }

        removeWorker(entry);
        entry.progress = 0;
        entry.workerFailureCount++;
        if (entry.workerFailureCount <= properties.getRetriesOnWorkerFailure()) {
            queue.add(entry);
            fireOnProgress(entry);
            fireOnStatus(entry);
            log.warn("Re added job to queue "+entry);
        } else {
            log.error("Worker failure count exceeded limit ("+properties.getRetriesOnWorkerFailure()+") for entry: "+entry);
            log.error("Sending back exception");
            JobResultMessage jobResultMessage = new JobResultMessage(entry.jobMessage.getJobID());
            jobResultMessage.setMasterException(new WorkerExecutionException(""+entry.workerFailureCount+" workers dies while execution this job"));
            entry.result = jobResultMessage;
            fireOnResult(entry, jobResultMessage);
        }
    }


    // Private stuff

    private void fireOnStatus(JobEntry jobEntry) {
        String jobID = jobEntry.jobMessage.getJobID();
        JobStatus jobStatus = jobEntry.getStatus();
        if (jobEntry.clients == null || jobEntry.clients.isEmpty()) return;
        for (ServerLink client: getListeningClientsCloned(jobEntry)) {
            MessageSender.sendStatusToClient(jobID, jobStatus, client, failureHandler);
        }
    }

    private void fireOnProgress(JobEntry jobEntry) {
        String jobID = jobEntry.jobMessage.getJobID();
        if (jobEntry.clients == null || jobEntry.clients.isEmpty()) return;
        for (ServerLink client: getListeningClientsCloned(jobEntry)) {
            MessageSender.sendProgressToClient(jobID, jobEntry.progress, client, failureHandler);
        }
    }

    private void fireOnRunMethodRemoteResult(JobEntry jobEntry, RunMethodRemoteResultMessage runMethodRemoteResultMessage) {
        log.debug("fireOnRunMethodRemoteResult sending : " + runMethodRemoteResultMessage);
        if (jobEntry.clients == null || jobEntry.clients.isEmpty()){
            log.warn("No clients to send the result to .... " + runMethodRemoteResultMessage);
            return;
        }
        for (ServerLink client: getListeningClientsCloned(jobEntry)) {
            log.debug("fireOnRunMethodRemoteResult sending to ("+ client +") -> " + runMethodRemoteResultMessage);
            MessageSender.sendRunMethodRemoteResultOfToClient(runMethodRemoteResultMessage, client, failureHandler);
        }
    }


    private void fireOnResult(JobEntry jobEntry, JobResultMessage result) {
        MasterServer.increaseCounterStatus("/tmp/masterworker_output_jobs_count");

        log.debug("fireOnResult["+ result.getJobID() +"]:START");
        try {
            jobEntry.jobDoneDate = Calendar.getInstance();
            if (jobEntry.clients == null || jobEntry.clients.isEmpty()) return;
            for (ServerLink client : getListeningClientsCloned(jobEntry)) {
                log.debug("fireOnResult[" + result.getJobID() + "]:sendResultToClient(" + client + ")-START");
                MessageSender.sendResultToClientAndClose(result, client, new MessageSender.FailureHandler() {
                    public void onFailure(ServerLink client) {
                        log.error("fireOnResult[" + result.getJobID() + "]:sendResultToClient(" + client.getOtherHost() + ")-ERROR");
                        removeListener(client);
                    }});
                if (result.hasException()) {
                    MasterServer.increaseCounterStatus("/tmp/masterworker_output_error_jobs_count");
                }
                log.debug("fireOnResult[" + result.getJobID() + "]:sendResultToClient(" + client.getOtherHost() + ")-DONE");
            }
        } catch (Exception e){
            log.error("Some error with Job("+ result.getJobID() +"):" + e, e);
        }
        log.debug("fireOnResult["+ result.getJobID() +"]:BEFORE-REMOVE");
        removeJob(result.getJobID());
        log.debug("fireOnResult["+ result.getJobID() +"]:ENDS");
    }

    protected synchronized void removeJob(String jobID) {
        JobEntry jobEntry = pool.get(jobID);
        log.debug("Removed job["+ jobID +"]: "+ jobEntry);
        if (jobEntry != null) {
            pool.remove(jobID);
            queue.remove(jobEntry);
        }
        File storedResultFile = MasterServer.getStoredResultFile(jobID);
        if(storedResultFile != null && storedResultFile.exists()){
            storedResultFile.delete();
        }

    }

    @SuppressWarnings("unchecked cast")
    private HashSet<ServerLink> getListeningClientsCloned(JobEntry jobEntry) {
        return (HashSet<ServerLink>) jobEntry.clients.clone();
    }

    private void setWorker(JobEntry jobEntry, ServerLink worker) {
        try {
            log.debug("setWorker job["+ jobEntry.getJobID() +"]: START ");
            workerMap.put(worker, jobEntry.jobMessage.getJobID());
            jobEntry.worker = worker;
            log.debug("setWorker job["+ jobEntry.getJobID() +"]: DONE ");
        } catch (Exception e){
            log.error("setWorker job["+ jobEntry.getJobID() +"]: FAILED:  " + e, e);
        }
    }

    private synchronized void removeWorker(JobEntry jobEntry) {
        workerMap.remove(jobEntry.worker);
        jobEntry.worker = null;
    }

    public void kill(String jobID) {
        removeJob(jobID);
    }

    public JobEntry getJobEntry(String jobID) {
        JobEntry jobEntry = pool.get(jobID);
        return jobEntry;
    }

    public JobEntry getJobEntry(ServerLink client) {
        String jobID = clientMap.get(client);
        if(jobID != null){
            JobEntry jobEntry = pool.get(jobID);
            return jobEntry;
        } else {
            return null;
        }
    }

    public String getJobID(ServerLink client) {
        String jobID = clientMap.get(client);
        return jobID;
    }

    public synchronized List<JobEntry> getDiffJobs(String executorClassName) {
        List<JobEntry> toReturn = new ArrayList<>();
        HashSet<String> haveAlready = new HashSet<>();
        for(Iterator<JobEntry> iterator = queue.iterator(); iterator.hasNext(); ){
            JobEntry next = iterator.next();
            String newExe = next.jobMessage.getExecutorClassName();
            if(!newExe.equals(executorClassName) && !haveAlready.contains(newExe)){
                haveAlready.add(newExe);
                toReturn.add(next);
            }
        }
        return toReturn;
    }


    class JobEntry {

        public final int sequenceNumber;
        public JobMessage jobMessage;
        public HashSet<ServerLink> clients;
        public JobResultMessage result;
        public double progress = 0;
        public double priority = 1;
        public Calendar date = Calendar.getInstance();
        public Calendar jobTakenDate = null;
        public Calendar jobDoneDate = null;
        public ServerLink worker;
        public int workerFailureCount = 0;

        private JobEntry(JobMessage jobMessage) {
            this.sequenceNumber = jobEntrySequenceNumberCounter;
            jobEntrySequenceNumberCounter++;
            this.jobMessage = jobMessage;
        }

        public String getJobID(){
            return jobMessage.getJobID();
        }


        public JobStatus getStatus() {
            if (result != null) return JobStatus.DONE;
            else if (worker != null) return JobStatus.IN_PROGRESS;
            else return JobStatus.QUEUED;
        }

        public void runMethodRemote(RunMethodRemoteBeanMessage runMethodRemoteBeanMessage, ServerLink serverLink) throws IOException {
            int counter = 0;
            while (getStatus().equals(JobStatus.QUEUED) && serverLink.isWorking()) {
                try {
                    if(counter++ % 20 == 0){
                        log.error("JobEntry thread has no assigned worker. The thread with sleep for 150ms and recheck");
                        if(!serverLink.ping()){
                            serverLink.close();
                            break;
                        }
                    }
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    log.error("JobEntry thread was interrupted " + e.getMessage());
                }
            }
            if(serverLink.isWorking()){
                log.debug("Client said goodbye.... ");
            }
            if(worker == null){
                removeJob(getJobID());
            } else {
                worker.write(runMethodRemoteBeanMessage);
            }
        }


        @Override
        public String toString() {
            return String.format("{Job-%s:%s status=%s, result=%s progress=%s, priority=%s, rerun=%s,  w=%s, created=%s, taken=%s, result=%s}",
                    getSimpleName(jobMessage.getExecutorClassName()),
                    jobMessage.getJobID(),
                    getStatus(),
                    getResultType(result),
                    progress,
                    priority,
                    workerFailureCount,
                    (worker != null ? "" + worker.getOtherHost() +":"+ worker.getOtherPort() : "null"),
                    (new SimpleDateFormat("yyyyMMMdd HH:mm:ss")).format(date.getTime()),
                    (jobTakenDate != null ? (new SimpleDateFormat("MMMdd HH:mm:ss")).format(jobTakenDate.getTime()) : "-"),
                    (jobDoneDate != null ? (new SimpleDateFormat("MMMdd HH:mm:ss")).format(jobDoneDate.getTime()) : "-")
                    );
        }


        private String getResultType(JobResultMessage result) {
            if (result == null) return "None";
            else if (result.hasException()) return "Exception";
            else return "Value";
        }

        private String getSimpleName(String name) {
            if(name == null || name.indexOf(".") == -1) return "" + name;
            return name.substring(name.lastIndexOf('.')+1);
        }

    }


    private class PriorityComparator implements Comparator<JobEntry> {
        public int compare(JobEntry entry1, JobEntry entry2) {
            if (entry1.equals(entry2)) return 0;
            int c1 = Double.compare(entry1.priority, entry2.priority);
            if (c1 != 0) return c1;
            else return entry1.sequenceNumber - entry2.sequenceNumber;
        }
    }

    public List<ObserverJobMessage> getObserverJobMessageList() {
        ArrayList<ObserverJobMessage> list = new ArrayList<ObserverJobMessage>();
        for (JobEntry jobEntry: pool.values()) {
            list.add(createObserverJobMessage(jobEntry));
        }
        return list;
    }

    private ObserverJobMessage createObserverJobMessage(JobEntry jobEntry) {
        ObserverJobMessage job = new ObserverJobMessage();
        job.setJobID(jobEntry.jobMessage.getJobID());
        job.setSequenceNumber(jobEntry.sequenceNumber);
        job.setDate(jobEntry.date);
        job.setExecutorClassName(jobEntry.jobMessage.getExecutorClassName());
        if (jobEntry.clients != null) {
            ArrayList<String> clients = new ArrayList<String>(jobEntry.clients.size());
            for (ServerLink client: jobEntry.clients) {
                clients.add(client.getOtherHostPort());
            }
            job.setListeningClients(clients);
        }
        job.setPriority(jobEntry.priority);
        job.setProgress(jobEntry.progress);
        job.setSerializedJobData(jobEntry.jobMessage.getSerializedJobData());
        job.setStatus(jobEntry.getStatus());
        if (jobEntry.worker != null) {
            job.setWorker(jobEntry.worker.getOtherHostPort());
        }
        job.setWorkerFailureCount(jobEntry.workerFailureCount);
        return job;
    }


    @Override
    public String toString() {
        try {
            Calendar oneHour = Calendar.getInstance();
            oneHour.add(Calendar.MINUTE, -10);

            StringBuilder builder = new StringBuilder();
            builder.append("---------------------------------- JobPool ----------------------------------\n");
            builder.append("IN QUEUE:\n");
            JobEntry[] jobEntries = queue.toArray(new JobEntry[queue.size()]);
            for (int i = 0; i < jobEntries.length; i++) {
                JobEntry jobEntry = jobEntries[i];
                builder.append("  ");
                builder.append(jobEntry.toString());
                builder.append("\n");
            }
            builder.append("OTHERS:\n");
            List<String> jobsToRemove = new ArrayList<String>();
            for (JobEntry jobEntry : pool.values()) {
                if (queue.contains(jobEntry)) continue;
                builder.append("  ");
                builder.append(jobEntry.toString());
                builder.append("\n");
                if (jobEntry.getStatus() == JobStatus.DONE && jobEntry.jobDoneDate != null && jobEntry.jobDoneDate.before(oneHour)) {
                    jobsToRemove.add(jobEntry.getJobID());
                }
            }
            for (String sid : jobsToRemove) {
                try {
                    removeJob(sid);
                } catch (Exception e) {
                    log.error("Some error: " + e, e);
                }
            }


            builder.append("LAST-5-RESULTS:\n");
            for (Iterator<JobEntry> iterator = last5SuccesJobs.iterator(); iterator.hasNext(); ) {
                JobEntry jobEntry = iterator.next();
                builder.append("  ");
                builder.append(jobEntry.toString());
                builder.append("\n");
            }
            builder.append("STATS: ");
            builder.append("Last-success(" + (lastResult != null ? "" + lastResult.getTime() : " -NO RESULTS.... yet...! ") + ") ");
            builder.append("queue.size(" + queue.size() + ") ");
            builder.append("TOTAL(" + resultTotalCounter + ") ");
            builder.append("LAST-" + (resultTotalCounter % 100) + "-AVG(" + ((System.currentTimeMillis() - resultLast100Time) / (1 + (resultTotalCounter % 100))) + ") ");
            builder.append('\n');
            return builder.toString();
        } catch (Exception e){
            e.printStackTrace();
            log.warn("Error with toString: " + e, e);
            return null;
        }
    }
}
