package dk.lessismore.nojpa.masterworker.client;

import dk.lessismore.nojpa.masterworker.JobStatus;
import dk.lessismore.nojpa.masterworker.messages.RunMethodRemoteBeanMessage;
import dk.lessismore.nojpa.masterworker.messages.RunMethodRemoteResultMessage;
import dk.lessismore.nojpa.masterworker.executor.Executor;
import dk.lessismore.nojpa.masterworker.exceptions.JobHandleClosedException;
import dk.lessismore.nojpa.guid.GuidFactory;
import dk.lessismore.nojpa.concurrency.WaitForValue;
import dk.lessismore.nojpa.utils.Pair;
import org.apache.log4j.Logger;

import java.util.HashMap;

public class JobHandle<O> {

    private static Logger log = org.apache.log4j.Logger.getLogger(JobHandle.class);

    private final JobHandleToMasterProtocol<O> jm;
    private final Class<? extends Executor> implementationClass;
    private final Object jobData;
    private final String jobID = GuidFactory.getInstance().makeGuid();
    private boolean closed = false;

    // Default job values
    private JobStatus jobStatus = JobStatus.QUEUED;
    private double jobProgress = 0;
    private WaitForValue<Pair<O, RuntimeException>> result = new WaitForValue<Pair<O, RuntimeException>>();
    private HashMap<String, WaitForValue<Pair<Object, RuntimeException>>> runMethodRemoteResultMap = new HashMap<String, WaitForValue<Pair<Object, RuntimeException>>>();


    /**
     * Queue job in the master-worker setup and create a handle to obtain
     * status information and the result of the job.
     * @param jm helper object implementing the network logic.
     * @param executorClass Class carrying the algorithem of the job.
     * @param jobData Input for the executor class's run method.
     */
    public JobHandle(JobHandleToMasterProtocol<O> jm, Class<? extends Executor> executorClass, Object jobData) {
        this.jm = jm;
        this.implementationClass = executorClass;
        this.jobData = jobData;
        jm.sendRunJobRequest(jobID, executorClass, jobData);
        jm.addJobListener(new Listener(), jobID);
    }

    public JobHandle(JobHandleToMasterProtocol<O> jm, String jobID) {
        this.jm = jm;
        this.implementationClass = null;
        this.jobData = null;
        jm.addJobListener(new Listener(), jobID);
    }


    public Class<? extends Executor> getImplementationClass() {
        return implementationClass;
    }

    public Object getJobData() {
        return jobData;
    }

    public String getJobID() {
        return jobID;
    }

    /**
     * @return The last known job status from master.
     */
    public JobStatus getStatus() {
        if (closed) throw new JobHandleClosedException();
        return jobStatus;
    }

    /**
     * @return The last known job progress from master.
     */
    public double getProgress() {
        if (closed) throw new JobHandleClosedException();
        return jobProgress;
    }

    /**
     * Case Status of job
     *   QUEUE: Dequeue job.
     *   RUNNING: Try to stop execution on worker by setting the stopNicely flag on the executer object.
     *   DONE: Descard result.
     * Close this handle.
     */
    public void stopNicely() {
        if (closed) throw new JobHandleClosedException();
        jm.stopNicely();
        close();
    }

    /**
     * Case Status of job
     *   QUEUE: Dequeue job.
     *   RUNNING: Terminate execution on worker.
     *   DONE: Descard result.
     * Close this handle.
     */
    public void kill() {
        if (closed) throw new JobHandleClosedException();
        jm.kill();
        close();
    }

    public Object runMethodRemote(RunMethodRemoteBeanMessage runMethodRemoteBeanMessage)  throws Throwable {
        try{
            runMethodRemoteBeanMessage.setJobID( jobID );
            WaitForValue<Pair<Object, RuntimeException>> waitForValue = new WaitForValue<Pair<Object, RuntimeException>>();
            runMethodRemoteResultMap.put(runMethodRemoteBeanMessage.getMethodID(), waitForValue);
            jm.runMethodRemote(runMethodRemoteBeanMessage);
            Pair<Object, RuntimeException> pair = waitForValue.getValue();
            Object value = pair.getFirst();
            RuntimeException exception = pair.getSecond();
            if (exception != null) {
                throw exception;
            } else {
                if(value == null) return null;
                if(value instanceof RunMethodRemoteResultMessage){
                    RunMethodRemoteResultMessage msg = (RunMethodRemoteResultMessage) value;
                    if(msg.hasException()){
                        throw msg.getException(jm.serializer);
                    } else {
                        Object o = msg.getResult(jm.serializer);
                        return o;
                    }
                }
                return value;
            }
        } finally {
            runMethodRemoteResultMap.remove(runMethodRemoteBeanMessage.getMethodID());
        }

    }



    /**
     * @return The result of the job executed on the master-worker system.
     * It will block until the job is done or an exception occurs.
     * Exceptions may come from the execution of the algorithm, or from network communication problems.
     * Checked Exception (Errors) thrown from the executed job are wrapped in a WrappedErrorException.
     */
    public O getResult() {
        if (closed) throw new JobHandleClosedException();
        Pair<O, RuntimeException> pair = result.getValue();
        O value = pair.getFirst();
        RuntimeException exception = pair.getSecond();
        if (exception != null) {
            throw exception;
        } else {
            jobProgress = 1;
            return value;
        }
    }

    /**
     * Add callback listener for job status changes and the final job result.
     * @param listener Callback
     */
    public void addJobListener(JobListener<O> listener) {
        if (closed) throw new JobHandleClosedException();
        jm.addJobListener(listener, jobID);
    }

    /**
     * Remove callback listener.
     * @param listener Callback
     */
    public void removeJobListener(JobListener<O> listener) {
        if (closed) throw new JobHandleClosedException();
        jm.removeJobListener(listener);
    }

    /**
     * TODO is this smart ?
     * Unregister all listeners created with this handle and close connection to master.
     * Subsequent calls to getStatus(), getProgress(), stopNicely(), kill(),
     * close(), getResult() and addJobListener will raise JobHandleClosedException.
     */
    public void close() {
        if (closed) throw new JobHandleClosedException();
        closed = true;
        jm.close();
        result.setValue(new Pair<O, RuntimeException>(null, new JobHandleClosedException()));
    }



    private class Listener implements JobListener<O> {

        public void onStatus(JobStatus status) {
            jobStatus = status;
        }

        public void onProgress(double progress) {
            jobProgress = progress;
        }

        public void onResult(O value) {
            result.setValue(new Pair<O, RuntimeException>(value, null));
            jobProgress = 1;
            jobStatus = JobStatus.DONE;
        }

        public void onRunMethodRemoteResult(RunMethodRemoteResultMessage runMethodRemoteResultMessage) {
            WaitForValue<Pair<Object, RuntimeException>> waitForValue = runMethodRemoteResultMap.get(runMethodRemoteResultMessage.getMethodID());
            if(waitForValue != null){
                log.debug("Setting result on waitForValue : result("+ result +")");
                waitForValue.setValue(new Pair<Object, RuntimeException>(runMethodRemoteResultMessage.getResult(jm.serializer), runMethodRemoteResultMessage.getException(jm.serializer)));
            } else {
                log.error("Got a RunMethodRemoteResultMessage ("+ runMethodRemoteResultMessage +") - but no client to give the result to .... :( ");
            }
        }


        public void onException(RuntimeException e) {
            result.setValue(new Pair<O, RuntimeException>(null, e));
            jobStatus = JobStatus.DONE;
        }
    }

}