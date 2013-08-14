package dk.lessismore.reusable_v4.reflection.db;

import dk.lessismore.reusable_v4.reflection.db.model.ModelObject;
import dk.lessismore.reusable_v4.reflection.db.model.ModelObjectInterface;

/**
 * Created by IntelliJ IDEA.
 * User: bart
 * Date: 2007-04-08
 * Time: 15:39:32
 * To change this template use File | Settings | File Templates.
 */
public interface DbObjectVisitor<K extends ModelObjectInterface> {

    void visit(K m);

    void setDone(boolean b);

    boolean getDone();    

}
