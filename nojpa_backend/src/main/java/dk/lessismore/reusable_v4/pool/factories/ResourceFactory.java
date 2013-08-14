package dk.lessismore.reusable_v4.pool.factories;

/**
 * This is the interface for the resource factory which is used by the
 * 'RessourcePool' class, to create instances of objects which the pool
 * consist of. The interface has a make and a close function. The reason for
 * the close function is that often the objects (database or socket connections)
 * has to be closed proberly again.
 *
 * @author LESS-IS-MORE ApS
 * @version 1.0 14-01-02
 */
public interface ResourceFactory {

    /**
     * This function makes an instance of a predefined class which the
     * pool should consist of. The function should handle all initializing
     * of the object.
     * @return A pool object.
     */
    public Object makeResource();

    /**
     * This function closes a pool object. This could be closing of an
     * database connection or termination of a thread.
     */
    public void closeResource(Object resource);
}
