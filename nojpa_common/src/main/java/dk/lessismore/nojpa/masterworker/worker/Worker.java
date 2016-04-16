package dk.lessismore.nojpa.masterworker.worker;

import dk.lessismore.nojpa.concurrency.WaitForValue;
import dk.lessismore.nojpa.masterworker.executor.Executor;
import dk.lessismore.nojpa.masterworker.master.MasterProperties;
import dk.lessismore.nojpa.net.link.ClientLink;
import dk.lessismore.nojpa.serialization.XmlSerializer;
import dk.lessismore.nojpa.serialization.Serializer;
import dk.lessismore.nojpa.masterworker.messages.RegistrationMessage;
import dk.lessismore.nojpa.masterworker.messages.HealthMessage;
import dk.lessismore.nojpa.masterworker.messages.*;
import dk.lessismore.nojpa.masterworker.*;
import dk.lessismore.nojpa.masterworker.bean.worker.BeanExecutor;
import dk.lessismore.nojpa.masterworker.exceptions.JobDoesNotExistException;
import dk.lessismore.nojpa.masterworker.exceptions.MasterUnreachableException;
import dk.lessismore.nojpa.properties.PropertiesProxy;

import java.util.Arrays;
import java.util.List;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;


public class Worker {

    private static final MasterProperties properties = PropertiesProxy.getInstance(MasterProperties.class);
    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(Worker.class);
    private static final long SEND_PROGRESS_INTERVAL = 10 * 1000;
    private static final long SEND_HEALTH_INTERVAL = 120 * 1000;
    private final List<? extends Class<? extends Executor>> supportedExecutors;
    private static final double CRITICAL_VM_MEMORY_USAGE = 0.95;
    private final Serializer serializer;

    private final LinkAndThreads linkAndThreads = new LinkAndThreads();

    private boolean stop = false;

    public Worker(Serializer serializer, List<? extends Class<? extends Executor>> supportedExecutors) {
        this.serializer = serializer;
        this.supportedExecutors = supportedExecutors;
        run();
    }

    public Worker(Serializer serializer, Class<? extends Executor> ... supportedExecutors) {
        this(serializer, Arrays.asList(supportedExecutors));
    }

    public Worker(List<? extends Class<? extends Executor>> supportedExecutors) {
        this (new XmlSerializer(), supportedExecutors);
    }

    public Worker(Class<? extends Executor> ... supportedExecutors) {
        this (new XmlSerializer(), supportedExecutors);
    }


    /**
     * Runs one job and then exits if memory usage is critical high.
     * It should be run as it's own process.
     * It is supposed to be run in a loop (probably a batch script) that restarts it when it quits.
     */
    public void run() {
        String host = properties.getHost();
        int port = properties.getWorkerPort();

        while(true && !stop) {
            log.debug("Trying to establish connection to Master on "+host+":"+port);
            //final ClientLink clientLink;

            if(linkAndThreads.clientLink == null) {
                try {
                    linkAndThreads.clientLink = new ClientLink(host, port);
                    linkAndThreads.clientLink.write(new RegistrationMessage(getSupportedExecutors()));
                    linkAndThreads.startThreads();
                } catch (ConnectException e) {
                    throw new MasterUnreachableException("Failed to connect to Master on " + host + ":" + port, e);
                } catch (IOException e) {
                    throw new MasterUnreachableException("Failed to connect to Master on " + host + ":" + port, e);
                }
            }


            log.debug("Waiting for job");
            final JobMessage jobMessage = linkAndThreads.waitForValue.getValue();
            if(jobMessage == null){
                String message = "Exception .. jobMessage == null";
                log.error(message, new Exception(message));
                stop = true;
                linkAndThreads.stopThreads();
                break;
            }
            linkAndThreads.executor = loadExecutor(jobMessage);
            final Object input = serializer.unserialize(jobMessage.getSerializedJobData());

            final JobResultMessage<Object> resultMessage = new JobResultMessage<Object>(jobMessage.getJobID());

            log.debug("Job received... jobMessage.getJobID("+ jobMessage.getJobID() +")");

            linkAndThreads.jobThread = new Thread(new Runnable() {
                public void run() {
                    runJob(linkAndThreads.executor, input, resultMessage);
                }
            });
            linkAndThreads.jobThread.setDaemon(true);
            linkAndThreads.jobThread.start();

            log.debug("Job running...");


            log.debug("Waiting for calculation to end...");

            double progress = -1.0;
            while(linkAndThreads.jobThread.isAlive() && linkAndThreads.clientLink.isWorking()) {
                if(linkAndThreads.executor.getProgress() != progress) {
                    progress = linkAndThreads.executor.getProgress();
                    log.debug("Working: "+(progress*100) + "%");
                    try {
                        linkAndThreads.clientLink.write(new JobProgressMessage(jobMessage.getJobID(), progress));
                    } catch (IOException e) {
                        log.error("IOException while writing back progress. Closing link",e);
                        linkAndThreads.clientLink.close();
                        break;
                    }
                }
                try {
                    linkAndThreads.jobThread.join(SEND_PROGRESS_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            log.debug("Writeting back result...");
            try {
                linkAndThreads.clientLink.write(resultMessage);
            } catch(IOException e) {
                log.error("Error when writing job result to master: ", e);
                linkAndThreads.clientLink.close();
                linkAndThreads.stopThreads();
                break;
            }

            if (SystemHealth.getVmMemoryUsage() > CRITICAL_VM_MEMORY_USAGE) {
                log.warn("Worker has a critical high VM memory usage.");
                stop = true;
                linkAndThreads.clientLink.close();
                linkAndThreads.stopThreads();
                break; //exit
            }
//            stop = true;
//            break;

        } // end loop
        try{
            linkAndThreads.clientLink.close();
            linkAndThreads.stopThreads();
        } catch (Exception e){}
        log.debug("Exiting!");
    }

    private Executor<Object, Object> loadExecutor(JobMessage jobMessage) {
        try {
            final String className = jobMessage.getExecutorClassName();
            final Class executorClass = Class.forName(className);
            return (Executor) executorClass.newInstance();
        } catch (Exception e) {
            log.error("Error extracting executer from job message. ", e);
            throw new RuntimeException(e);
        }
    }

    private void runJob(Executor<Object, Object> executor, Object input, JobResultMessage<Object> resultMessage) {
        try {
            log.debug("Will run job: " + input);
            Object result = executor.run(input);
            if(executor.isStoppedNicely()) resultMessage.setException(new JobDoesNotExistException(), serializer);
            else resultMessage.setResult(result, serializer);
        } catch (Exception e) {
            resultMessage.setException(e, serializer);
        }
    }

    private List<? extends Class<? extends Executor>> getSupportedExecutors() {
        return supportedExecutors;
    }


    protected class LinkAndThreads {

        protected ClientLink clientLink = null;
        protected Thread healthReporterThread = null;
        protected Thread stopperThread = null;
        protected WaitForValue<JobMessage> waitForValue = new WaitForValue<JobMessage>();
        protected Thread jobThread = null;
        protected Executor executor = null;
        protected JobMessage maybeJob = null;


        public void stopThreads(){
            if(stopperThread != null){
                try {
                    stopperThread.interrupt();
                } catch (Exception e){}
            }
            if(healthReporterThread != null){
                try {
                    healthReporterThread.interrupt();
                } catch (Exception e){}
            }
        }

        public void startThreads(){
            healthReporterThread = new Thread(new Runnable() {
                public void run() {
                    while(!stop) {
                        try {
                            HealthMessage healthMessage = new HealthMessage(SystemHealth.getSystemLoadAverage(),
                                    SystemHealth.getVmMemoryUsage(), SystemHealth.getDiskUsages());
                            if(linkAndThreads.clientLink.isWorking()) {
                                linkAndThreads.clientLink.write(healthMessage);
                            }
                            Thread.sleep(SEND_HEALTH_INTERVAL);
                        } catch (IOException e) {
                            log.debug("IOException - shutting down healthReporterThread");
                            break;
                        } catch (InterruptedException e) {
//                            log.debug("Health report thread interrupted");
                        }
                    }
                }
            });
            healthReporterThread.setDaemon(true);
            healthReporterThread.start();



            stopperThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        while(!stop && linkAndThreads.clientLink.isWorking()) { //TODO loop unnecassary
                            Object o = linkAndThreads.clientLink.read();
                            if(o instanceof JobMessage) {
                                maybeJob = (JobMessage) o;
                                waitForValue.setValue(maybeJob);
                            } else if(o instanceof KillMessage) {
                                stop = true;
                                System.exit(0);
                            } else if(o instanceof StopMessage) {
                                log.info("Stop message recieved from master - signal executer to stop nicely.");
                                executor.stopNicely();
                            } else if(o instanceof KillMessage) {
                                log.info("Kill message recieved from master - shutting down.");
                                System.exit(0);
                            } else if(o instanceof RunMethodRemoteBeanMessage) {
                                RunMethodRemoteBeanMessage runMethodRemoteBeanMessage = (RunMethodRemoteBeanMessage) o;
                                BeanExecutor b = (BeanExecutor) executor;
                                Object resultOfMethod = null;
                                RunMethodRemoteResultMessage resultMessageOfMethod = new RunMethodRemoteResultMessage(maybeJob.getJobID());
                                resultMessageOfMethod.setMethodID( runMethodRemoteBeanMessage.getMethodID() );
                                resultMessageOfMethod.setMethodName( runMethodRemoteBeanMessage.getMethodName() );
                                try{
                                    resultOfMethod = b.runMethod(runMethodRemoteBeanMessage);
                                    log.debug("Will set the of " + runMethodRemoteBeanMessage.getMethodName() + " -> result("+ resultOfMethod +") ");
//                                    if(resultOfMethod != null){ // TODO ??? - should we return the null?

                                    resultMessageOfMethod.setResult(resultOfMethod, serializer);
                                    try{
//                                            log.debug("Writing the result : " + resultMessageOfMethod);
                                        linkAndThreads.clientLink.write(resultMessageOfMethod);
                                    } catch (Exception e){
                                        log.error("Some error when writing back result ... Have been executed .. " + e, e);
                                    }
//                                    }
                                } catch(Exception t){
                                    log.error("Some error when calling remoteMethod: " + t, t);
                                    resultMessageOfMethod.setException( t , serializer);
                                    try{
                                        linkAndThreads.clientLink.write(resultMessageOfMethod);
                                    } catch (Exception e){
                                        log.error("Some error when writing back result ... Have been executed .. " + e, e);
                                    }
                                }
                                System.out.println("We will now run RemoteMethod on executor :" + executor);
                            } else {
                                log.error("Did not understand message from master (stop or kill message expected): " + o);
                            }

                        }
                    } catch(ClosedChannelException e) {
                        log.debug("Connection closed - Stopping stopperThread");
                        System.exit(0);
                    } catch(IOException e) {
                        log.warn("Some error in stopper jobThread: ", e);
                    }
                }
            });
            stopperThread.setDaemon(true);
            stopperThread.start();


        }


    }


}