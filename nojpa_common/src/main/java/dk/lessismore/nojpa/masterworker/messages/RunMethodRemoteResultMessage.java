package dk.lessismore.nojpa.masterworker.messages;

import dk.lessismore.nojpa.masterworker.exceptions.MasterWorkerException;
import dk.lessismore.nojpa.masterworker.exceptions.WrappedErrorException;
import dk.lessismore.nojpa.serialization.Serializer;
import dk.lessismore.nojpa.utils.SuperIO;

import java.util.Date;

/**
 * Created : by IntelliJ IDEA.
 * User: seb
 * Date: 25-10-2010
 * Time: 12:18:44
 * To change this template use File | Settings | File Templates.
 */
public class RunMethodRemoteResultMessage<O> extends JobRelatedMessage {

    private String exception;
    private String result;
    private MasterWorkerException masterException;
    private String methodID;
    private String methodName;

    public RunMethodRemoteResultMessage() {
    }

    public RunMethodRemoteResultMessage(String jobID) {
        super(jobID);
    }

    public RuntimeException getException(Serializer serializer) {
        return serializer.unserialize(exception);
    }

    public void setException(Exception exception, Serializer serializer) {
        if (exception instanceof RuntimeException) {
            this.exception = serializer.serialize(exception);
        } else {
            this.exception = serializer.serialize(new WrappedErrorException(exception));
        }
        result = null;
    }

    public O getResult(Serializer serializer) {
        return (O)serializer.unserialize(result);
    }

    public void setResult(O result, Serializer serializer) {
        this.result = result == null ? null : serializer.serialize(result);
        exception = null;
    }

    public void setMasterException(MasterWorkerException masterException) {
        this.masterException = masterException;
        this.result = null;
    }

    public boolean hasException() {
        return exception != null;
    }

    public RuntimeException getMasterException() {
        return masterException;
    }

    public boolean hasMasterException() {
        return masterException != null;
    }

    public String getMethodID() {
        return methodID;
    }

    public void setMethodID(String methodID) {
        this.methodID = methodID;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public String toString() {
        return super.toString() + " result("+ (result != null && result.length() > 300 ? result.substring(0, 300) + " ...zipped..." : result) +") + exception("+ exception +")";
    }
}
