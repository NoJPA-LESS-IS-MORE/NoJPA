package dk.lessismore.nojpa.masterworker.master;

import dk.lessismore.nojpa.properties.PropertiesProxy;
import dk.lessismore.nojpa.resources.PropertyResources;
import dk.lessismore.nojpa.resources.PropertyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;

public class Master {

    private static final Logger log = LoggerFactory.getLogger(Master.class);

    private static final MasterProperties properties = PropertiesProxy.getInstance(MasterProperties.class);

    /**
     * Run Master listening on ports specified in property file.
     *
     * @param args Not used.
     * @throws java.io.IOException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Master.java:1");
        PropertyResources resources = PropertyService.getInstance().getPropertyResources(Master.class);
        System.out.println("resources = " + resources.getString("clientPort"));

        final MasterServer server = new MasterServer();
        final ServerSocket clientSocket = new ServerSocket(properties.getClientPort());
        final ServerSocket workerSocket = new ServerSocket(properties.getWorkerPort());
        final ServerSocket observerSocket = new ServerSocket(properties.getObserverPort());
        clientSocket.setSoTimeout(0);
        workerSocket.setSoTimeout(0);
        observerSocket.setSoTimeout(0);
        System.out.format("Master is now running on %s using client port %s, worker port %s and observer port %s\n",
                properties.getHost(), properties.getClientPort(), properties.getWorkerPort(), properties.getObserverPort());

        Thread clientAcceptThread = new Thread(new Runnable() {
            public void run() {
                while (true)
                    try {
                        server.acceptClientConnection(clientSocket);
                    } catch (Exception e) {
                        log.error("error in acceptClientConnection: {}", e.getMessage(), e);
                    }
            }
        });
        Thread workerAcceptThread = new Thread(new Runnable() {
            public void run() {
                while (true) try {
                    server.acceptWorkerConnection(workerSocket);
                } catch (Exception e) {
                    log.error("error in acceptWorkerConnection: {}", e.getMessage(), e);
                }
            }
        });
        Thread observerAcceptThread = new Thread(new Runnable() {
            public void run() {
                while (true)
                    try {
                        server.acceptObserverConnection(observerSocket);
                    } catch (Exception e) {
                        log.error("error in acceptObserverConnection: {}", e.getMessage(), e);
                    }
            }
        });

        Thread queueToWorkerThread = new Thread(new Runnable() {
            public void run() {
                while (true)
                    try {
                        server.runJobs();
                    } catch (Exception e) {
                        log.error("error in queueToWorkerThread: {}", e.getMessage(), e);
                    }
            }
        });

        Thread printStatusThread = new Thread(new Runnable() {
            public void run() {
                while (true)
                    try {
                        server.printStatus();
                        Thread.sleep(5 * 1000);
                    } catch (Exception e) {
                        log.error("error in queueToWorkerThread: {}", e.getMessage(), e);
                    }
            }
        });

        Thread pingerThread = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(20 * 1000);
                } catch (InterruptedException e) {

                }
                while (true)
                    try {
                        for(int i = 0; i < 12; i++){ //5 * 12 = 1 min
                            Thread.sleep(5 * 1000);
                            try{
                                server.pingAllWorkers();
                                server.pingAllClients();
                            } catch (Exception e){
                                log.debug("Some error: " + e, e);
                            }
                        }
                        server.sendAllHealthMessages();
                    } catch (Exception e) {
                        log.error("error in queueToWorkerThread: {}", e.getMessage(), e);
                    }
            }
        });

        //TODO: The master should start pingers + HealthMessage - no other should start pingers


        ObserverNotifierThread observerNotifierThread = new ObserverNotifierThread(server);

        pingerThread.setName("pingerThread");
        pingerThread.start();

        printStatusThread.setName("printStatusThread");
        printStatusThread.start();

        queueToWorkerThread.setName("queueToWorkerThread");
        queueToWorkerThread.start();

        clientAcceptThread.setName("clientAcceptThread");
        clientAcceptThread.start();

        workerAcceptThread.setName("workerAcceptThread");
        workerAcceptThread.start();

        observerAcceptThread.setName("observerAcceptThread");
        observerAcceptThread.start();

        observerNotifierThread.setName("observerNotifierThread");
        observerNotifierThread.start();

        clientAcceptThread.join();
        workerAcceptThread.join();
        observerAcceptThread.join();

        observerNotifierThread.stopThread();
        observerNotifierThread.join();
    }
}
