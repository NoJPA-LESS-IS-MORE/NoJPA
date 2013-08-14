package dk.lessismore.reusable_v4.reflection.db.attributes;

import dk.lessismore.reusable_v4.reflection.attributes.*;
import dk.lessismore.reusable_v4.reflection.db.model.*;
import dk.lessismore.reusable_v4.db.*;

import javax.persistence.Column;
import java.io.*;
import java.lang.annotation.Annotation;

/**
 * This represents an attribute in an database table. It is a wrap around the standard
 * <tt>Attribute</tt> class; but adds a lot of new features; which is only interresting
 * when talking about tables. This is:
 * <ul>
 * <li>A flag indicating if this attribute is a primary key.
 * <li>A flag indicating wether it is a singel association or a multi association (array).
 * <li>A flag indicating wether the multi association is an array of primitives.
 * <li>The data type of the attribute in the database table.
 * </ul>
 * An association is when the attribute is of a class type which is not a database primitive; like
 * String, int or Date. It is an instance of a class which has its own table in the database; and therefor
 * its the attribute accually contains an reference id /association to an tupel in an other table.
 * But why differentiate between singel and multi association ? Well if the attribute has more than
 * one association to another table; which is the case for an array or list; there is not
 * a one to one association. To solve this problem we need an extra association table, where
 * the object which the attribute belongs to is paired with the reference ids of the associated
 * tupels in the associated table.
 * If the multiassociation is an array of primitives and not of modelobjects, the association
 * table; will consist of the primary key of the object; and the different array elements.
 *
 * @author LESS-IS-MORE ApS
 * @version 1.0 21-5-2
 */
public class DbAttribute implements Serializable {

    /**
     * Is this attribute a primary key.
     */
    private boolean primaryKey = false;


    private boolean historyEnableIgnore = false;

    /**
     * The attribute which this Database attribute is based on.
     */
    private Attribute attribute = null;

    /**
     * The datatype which this attribute is mapped to; in the database.
     */
    private DbDataType dbDataType = null;

    /**
     * Is this attribute a multi association. (an array; of associtions to tupels in an
     * other table.)
     */
    private boolean multiAssociation = false;

    /**
     * Indicates that this attribute is an array of an primitive and should have an
     * association table.
     */
    private boolean primitivArrayAssociation = false;

    /**
     * Is this an association to an tupel in an other table.
     */
    private boolean association = false;

    //private int columnIndex = -1;

    public DbAttribute() {
    }


    private int nrOfCharacters = 250;
    public void setNrOfCharacters(int nrOfCharacters) {
        this.nrOfCharacters = nrOfCharacters;
    }

    public int getNrOfCharacters() {
        return nrOfCharacters;
    }

//    public int getColumnIndex() {
//        return columnIndex;
//    }
//
//    public void setColumnIndex(int columnIndex) {
//        this.columnIndex = columnIndex;
//    }


    public boolean getHistoryEnableIgnore() {
        return historyEnableIgnore;
    }

    public void setHistoryEnableIgnore(boolean historyEnableIgnore) {
        this.historyEnableIgnore = historyEnableIgnore;
    }

    /**
     * Call this after making a new instance of the class. This method initializes the
     * the instance; and determines wether its an association; the datatype and so one.
     */
    public void setAttribute(Attribute attribute) {
        this.attribute = attribute;
        dbDataType = new DbDataType();
        dbDataType.setDbAttribute(this);
        association = ModelObjectInterface.class.isAssignableFrom(attribute.getAttributeClass());
        if (!association) {
            association = attribute.isArray();
            if (association) {
                primitivArrayAssociation = true;
            }
        }

        if (!association) {
            if(attribute.getAttributeClass().equals(String.class)){
                Annotation[] as = attribute.getDeclaredAnnotations();
                if(as != null && as.length > 0){
                  for(int i = 0; i < as.length; i++){
                      if(as[i] instanceof Column){
                        Column c = (Column) as[i];
                        setNrOfCharacters(c.length());
                      }
                  }
                }
            }


            dbDataType.setType(attribute.getAttributeClass());
        } else {
            multiAssociation = attribute.isArray();
            if (!multiAssociation) {
                dbDataType.setType(DbDataType.DB_VARCHAR);
            } else if (primitivArrayAssociation) {
                dbDataType.setType(attribute.getAttributeClass());
            }
        }
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public String getAttributeName() {
        return attribute.getAttributeName();
    }

    /**
     * The name of the class which this attribute maps to. The name is not the
     * full classpath; but only the name like <tt>DbAttribute</tt>
     */
    public String getClassName() {
        return attribute.getAttributeClassName();
    }

    public Class getAttributeClass() {
        return attribute.getAttributeClass();
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public DbDataType getDataType() {
        return dbDataType;
    }

    public boolean isAssociation() {
        return association;
    }

    public boolean isMultiAssociation() {
        return multiAssociation;
    }

    public boolean isPrimitivArrayAssociation() {
        return primitivArrayAssociation;
    }

    public String toString() {
        return "DbAtt:" + attribute + " class=" + getClassName() + "\t\tisPrimaryKey=" + isPrimaryKey() + " isAssociation=" + isAssociation() + " isMultiAssociation=" + isMultiAssociation() + " DataType=" + getDataType();
    }

}
