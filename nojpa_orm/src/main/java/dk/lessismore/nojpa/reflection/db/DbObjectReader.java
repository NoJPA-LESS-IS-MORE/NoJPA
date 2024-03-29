package dk.lessismore.nojpa.reflection.db;

import dk.lessismore.nojpa.cache.ObjectCacheFactory;
import dk.lessismore.nojpa.db.DbDataType;
import dk.lessismore.nojpa.db.LimResultSet;
import dk.lessismore.nojpa.db.SQLStatementExecutor;
import dk.lessismore.nojpa.db.statements.SQLStatementFactory;
import dk.lessismore.nojpa.db.statements.SelectSQLStatement;
import dk.lessismore.nojpa.db.statements.WhereSQLStatement;
import dk.lessismore.nojpa.reflection.db.annotations.DbInline;
import dk.lessismore.nojpa.reflection.db.annotations.DbStrip;
import dk.lessismore.nojpa.reflection.db.attributes.DbAttribute;
import dk.lessismore.nojpa.reflection.db.attributes.DbAttributeContainer;
import dk.lessismore.nojpa.reflection.db.model.ModelObject;
import dk.lessismore.nojpa.reflection.db.model.ModelObjectProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * This class can read an tupel from a database into a model object which match the
 * table structure. To read the object from the database you need the primarykey of the
 * object; and the class. The class will allso load all associations which belong to
 * the object; but its possible to controle which associations that should be load with
 * the <tt>AssociationConstrain</tt> object. The load of these associations is
 * a new recursive call ! There is a built in cache; so that we do not have to load objects
 * which allready has been loaded. Enternal loops, when circular associations is made; is
 * allso prevented.
 *
 * @author LESS-IS-MORE
 * @version 1.0 21-5-2
 */
public class DbObjectReader {

    private static final Logger log = LoggerFactory.getLogger(DbObjectReader.class);

    public DbObjectReader() {
    }

    /**
     * This method reads an object from the database.
     *
     * @param objectId    the primary key id of the object in the table.
     * @param targetClass The class which the object is to be an instance of.
     * @return The object which has been read from the database; or null if it did not exists.
     */
    public static <T extends ModelObject> T readObjectFromDb(String objectId, Class<T> targetClass, boolean cache) {
        AssociationConstrain associationConstrain = new AssociationConstrain();
        HashMap map = new HashMap();
        return readObjectFromDb(objectId, targetClass, map, associationConstrain, "", cache);
    }

    /**
     * This method reads an object from the database.
     *
     * @param objectId    the primary key id of the object in the table.
     * @param targetClass The class which the object is to be an instance of.
     * @return The object which has been read from the database; or null if it did not exists.
     */
    public static Object readObjectFromDb(String objectId, Class targetClass) {
        AssociationConstrain associationConstrain = new AssociationConstrain();
        HashMap map = new HashMap();
        return readObjectFromDb(objectId, targetClass, map, associationConstrain, "", true);
    }

    public static Object readObjectFromCache(String objectId, Class targetClass) {
        ModelObject modelObject = (ModelObject) ObjectCacheFactory.getInstance().getObjectCache(targetClass).getFromCache(objectId);
        return modelObject;
    }

//    public static <T extends ModelObject> T  readObjectFromDb(String objectId, Class<T> targetClass) {
//        AssociationConstrain associationConstrain = new AssociationConstrain();
//        HashMap map = new HashMap();
//        return readObjectFromDb(objectId, targetClass, map, associationConstrain, "", true);
//    }

    /**
     * This method reads an object from the database.
     *
     * @param objectId             the primary key id of the object in the table.
     * @param targetClass          The class which the object is to be an instance of.
     * @param associationConstrain The association which is allowed or not allowed.
     * @return The object which has been read from the database; or null if it did not exists.
     */
    public static <T extends ModelObject> T readObjectFromDb(String objectId, Class<T> targetClass, AssociationConstrain associationConstrain) {
        HashMap map = new HashMap();
        return readObjectFromDb(objectId, targetClass, map, associationConstrain, "", true, null);
    }

    public static <T extends ModelObject> T readObjectFromDb(String objectId, Class<T> targetClass, AssociationConstrain associationConstrain, LimResultSet posibleResultSet) {
        HashMap map = new HashMap();
        return readObjectFromDb(objectId, targetClass, map, associationConstrain, "", true, posibleResultSet);
    }

    public static <T extends ModelObject> T readObjectFromDb(String objectId, Class<T> targetClass, AssociationConstrain associationConstrain, boolean cache) {
        HashMap map = new HashMap();
        return readObjectFromDb(objectId, targetClass, map, associationConstrain, "", cache, null);
    }

    /**
     * This method reads an object from the database.
     *
     * @param objectId             the primary key id of the object in the table.
     * @param associationConstrain The association which is allowed or not allowed.
     * @param attributePath        The concatenated attribute path so far (for recursive use only).
     * @return The object which has been read from the database; or null if it did not exists.
     */
    public static ModelObject readObjectFromDb(String objectId, DbAttribute dbAttribute, Map modelObjects, AssociationConstrain associationConstrain, String attributePath) {
        return readObjectFromDb(objectId, dbAttribute.getAttribute().getAttributeClass(), modelObjects, associationConstrain, attributePath, true, null);
    }

    public static ModelObject readObjectFromDb(String objectId, DbAttribute dbAttribute, Map modelObjects, AssociationConstrain associationConstrain, String attributePath, boolean cache) {
        return readObjectFromDb(objectId, dbAttribute.getAttribute().getAttributeClass(), modelObjects, associationConstrain, attributePath, cache, null);
    }

    /**
     * This method reads an object from the database.
     *
     * @param objectId             the primary key id of the object in the table.
     * @param targetClass          The class which the object is to be an instance of.
     * @param modelObjects         The objects which previously has been loaded.
     * @param associationConstrain The association which is allowed or not allowed.
     * @param attributePath        The concatenated attribute path so far (for recursive use only).
     * @return The object which has been read from the database; or null if it did not exists.
     */
    private static <T extends ModelObject> T readObjectFromDb(String objectId, Class<T> targetClass, Map modelObjects, AssociationConstrain associationConstrain, String attributePath, boolean cache) {
        return readObjectFromDb(objectId, targetClass, modelObjects, associationConstrain, attributePath, cache, null);
    }

    private static <T extends ModelObject> T readObjectFromDb(String objectId, Class<T> targetClass, Map modelObjects, AssociationConstrain associationConstrain, String attributePath, boolean cache, LimResultSet posibleResultSet) {
        LimResultSet limSet = null;
        try {
            //log.debug("Reading object: " + objectId + " " + targetClass);
            if (objectId == null || objectId.equals("null")) {
                log.warn("objectId == null ... returning null  ... targetClass("+ targetClass +") ", new Exception());
                return null;
            }
            //Does the object exists in the cache.
            ModelObject modelObject = (ModelObject) ObjectCacheFactory.getInstance().getObjectCache(targetClass).getFromCache(objectId);
            if (modelObject != null && cache) {
//                log.debug("readObjectFromDb::Found object in cache(" + objectId + ") targetClass("+ targetClass +")");
                //We have found the object in the cache. return it.
                return (T) modelObject;
            } else {
//                log.debug("readObjectFromDb::NOT found object in cache(" + objectId + ") targetClass("+ targetClass +")");
            }
//            log.debug("readObjectFromDb:1 "  + objectId);

            DbAttributeContainer dbAttributeContainer = DbClassReflector.getDbAttributeContainer(targetClass);
            if (dbAttributeContainer == null) {
                log.error("We do not have a container that match this class. We can not continue." , new Exception());
                return null;
            }
            //log.debug("readObjectFromDb:2 "  + objectId);
            //Have we been here before =>
            modelObject = (ModelObject) modelObjects.get(dbAttributeContainer.getClassName() + ":" + objectId);
            if (modelObject != null) {
                log.debug("readObjectFromDb: We have been here before; and can safely return this model object.");
                //log.debug("Loop found. using allready created object. ");
                return (T) modelObject;
            }

            if(targetClass.getAnnotation(DbInline.class) != null){
                return null;
            }
            try {
                modelObject = ModelObjectProxy.create(targetClass);
                modelObject = readIntoObjectFromDb(objectId, modelObject, targetClass, modelObjects, associationConstrain, attributePath, cache, posibleResultSet);
            } catch (Exception e) {
                log.error("Fatal error ... not Java-Bean constructor for class = " + targetClass.getName());
            }

            return (T) modelObject;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally{
            try{
                if(limSet != null && posibleResultSet == null){
                    limSet.close();
                    limSet = null;
                }
            } catch(Exception e){}

        }
    }

    public static <T extends ModelObject> T readIntoObjectFromDb(String objectId, ModelObject modelObject, Class<T> targetClass, Map modelObjects, AssociationConstrain associationConstrain, String attributePath, boolean cache, LimResultSet posibleResultSet) {
        LimResultSet limSet = null;
        try {

            DbAttributeContainer dbAttributeContainer = DbClassReflector.getDbAttributeContainer(targetClass);
            if (dbAttributeContainer == null) {
                log.error("We do not have a container that match this class. We can not continue." , new Exception());
                return null;
            }
            //log.debug("readObjectFromDb:2 "  + objectId);
            //Have we been here before =>
            ModelObject modelObjectFromMethodCache = (ModelObject) modelObjects.get(dbAttributeContainer.getClassName() + ":" + objectId);
            if (modelObjectFromMethodCache != null) {
                log.debug("readObjectFromDb: We have been here before; and can safely return this model object.");
                //log.debug("Loop found. using allready created object. ");
                return (T) modelObjectFromMethodCache;
            }

            if(targetClass.getAnnotation(DbInline.class) != null){
                return null;
            }
            //log.debug("readObjectFromDb:3 "  + objectId);
            //Construct an instance of the modelObject.
            modelObjects.put(dbAttributeContainer.getClassName() + ":" + objectId, modelObject);

            //Make the select statement which selects the tupel from the database.
            SelectSQLStatement selectStatement = (posibleResultSet != null ? null : makeSelectStatementToLoadObject(objectId, dbAttributeContainer));
            //log.debug("readObjectFromDb:6 "  + objectId);
            try {
                limSet = (posibleResultSet != null ? posibleResultSet : SQLStatementExecutor.doQuery(selectStatement));
                ResultSet resultSet = limSet.getResultSet();

                if (resultSet != null) {
                    //log.debug("readObjectFromDb:6.1 " + objectId);
                    boolean isPosNotNull = posibleResultSet != null;
                    boolean haveNext = isPosNotNull || resultSet.next();
                    //log.debug("readObjectFromDb:6.1.1: " + isPosNotNull + " " + haveNext);
                    if ((isPosNotNull || haveNext) && fillValuesIntoObject(dbAttributeContainer, modelObject, objectId, resultSet, modelObjects, associationConstrain, attributePath))
                    {
                        //log.debug("readObjectFromDb:6.2 " + objectId);
                        //loadMultiAssociations(modelObject, dbAttributeContainer, modelObjects, associationConstrain, attributePath, cache);
                    } else {
                        return null;
                    }
                }
                //log.debug("readObjectFromDb:7 " + objectId);
            } catch (Exception e) {
                log.error("error.close() = " + e);
                e.printStackTrace();
            } finally {
                try {
                    if (posibleResultSet == null) {
                        if(limSet != null){
                            limSet.close();
                            limSet = null;
                        }
                    }
                } catch (Exception exp) {
                    log.error("error.close() = " + exp);
                    exp.printStackTrace();
                }
            }
            modelObject.setNew(false);
            modelObject.setDirty(false);
//            if (cache && modelObject.isCachable()) {
//                //This model object has been fully loaded, with associations. And can be cached.
//                if (modelObject.getPrimaryKeyValue() != null) {
////                    log.debug("Adding in cache: "+ modelObject.getClass() + ":" + modelObject.getPrimaryKeyValue());
//                    ObjectCacheFactory.getInstance().getObjectCache(modelObject).putInCache(modelObject.getPrimaryKeyValue(), modelObject);
//                } else {
//                    log.warn("Problem!!! modelObject.getPrimaryKeyValue() = " + modelObject.getPrimaryKeyValue());
//                }
//            }
            return (T) modelObject;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally{
            try{
                if(limSet != null && posibleResultSet == null){
                    limSet.close();
                    limSet = null;
                }
            } catch(Exception e){}

        }
    }


    protected static void buildSqlNameQueryForDbAttributeContainer(DbAttributeContainer dbAttributeContainer){
        String sqlNameQuery = null;
        if (dbAttributeContainer.getSqlNameQuery() == null) {
            //NEW ************************************
            //log.debug("selectSqlStatement.makeStatement() = ");
            for (Iterator iterator = dbAttributeContainer.getDbAttributes().values().iterator(); iterator.hasNext();) {
                DbAttribute dbAttribute = (DbAttribute) iterator.next();

                //If the attribute is not an multi association.
                if ((!dbAttribute.isMultiAssociation() && !dbAttribute.isInlineInterface()) || (dbAttribute.isMultiAssociation() && dbAttribute.getAttributeClass().isEnum())) {
                    sqlNameQuery = (sqlNameQuery == null ? "" : sqlNameQuery + ", ") + (dbAttributeContainer.getTableName() + "." + (dbAttribute.getInlineAttributeName() != null ? dbAttribute.getInlineAttributeName() : dbAttribute.getAttributeName()));
                }
            }
            dbAttributeContainer.setSqlNameQuery(sqlNameQuery);
        }
    }



    /**                                         
     * This method can make a sql statement which can select the desired tupel from the table.
     *
     * @param objectId             The primary key of the object
     * @param dbAttributeContainer The attribute container of the object.
     */
    private static SelectSQLStatement makeSelectStatementToLoadObject(String objectId, DbAttributeContainer dbAttributeContainer) {

        SelectSQLStatement selectSQLStatement = SQLStatementFactory.getSelectSQLStatement();
        selectSQLStatement.addTableName(dbAttributeContainer.getTableName());
        selectSQLStatement.addConstrain(dbAttributeContainer.getPrimaryKeyAttribute().getAttributeName(), WhereSQLStatement.EQUAL, objectId);
        //Loop through the attributes.
        String sqlNameQuery = null;
        if (dbAttributeContainer.getSqlNameQuery() == null) {
            buildSqlNameQueryForDbAttributeContainer(dbAttributeContainer);
            sqlNameQuery = dbAttributeContainer.getSqlNameQuery();
        } else {
            sqlNameQuery = dbAttributeContainer.getSqlNameQuery();
        }
        selectSQLStatement.addAttributeName(sqlNameQuery);
        return selectSQLStatement;
    }

    /**
     * This method reads the fields from a result set into a object.
     *
     * @param dbAttributeContainer The attribute container of the object.
     * @param objectToFillInto     The object to fill the values into.
     * @param objectId             The primarykey of the object.
     * @param resultSet            The result set of the request to the database.
     * @param modelObjects         The objects which has been visited previously.
     * @param associationConstrain The association which is allowed or not allowed.
     * @param attributePath        The concatenated attribute path so far.
     */
    private static boolean fillValuesIntoObject(DbAttributeContainer dbAttributeContainer, ModelObject objectToFillInto, String objectId, ResultSet resultSet, Map modelObjects, AssociationConstrain associationConstrain, String attributePath) {
        try {
            //log.debug("fillValuesIntoObject:1");
            //if(resultSet.next()) {
            //log.debug("fillValuesIntoObject:2");
            //Read one attribute at a time from the result set.
            for (Iterator iterator = dbAttributeContainer.getDbAttributes().values().iterator(); iterator.hasNext();) {
                DbAttribute dbAttribute = (DbAttribute) iterator.next();
//                log.debug("Adding to object("+ objectToFillInto +")." + dbAttribute.getAttributeName());
                //If this attribute is an singel association.
                String attributeName = dbAttribute.getInlineAttributeName() != null ? dbAttribute.getInlineAttributeName() : dbAttribute.getAttributeName();
                if (dbAttribute.isAssociation() && !dbAttribute.isMultiAssociation()) {
                    if(dbAttribute.isInlineInterface()){
                        continue;
                    }



                    //String newAttributePath = AssociationConstrain.addAttributeToPath(attributePath, attributeName);
                    //if(associationConstrain.isNotAllowedAssociation(attributeName)) {
                    //We may not load this association.
                    //    objectToFillInto.setCachable(false);
                    //    continue;
                    //}
                    String associationId = resultSet.getString(attributeName);
                    objectToFillInto.setSingleAssociationID(attributeName, associationId);
                    //log.debug("fillValuesIntoObject::associationId = " + associationId + " associationId == null ? " + (associationId == null));
                    //Recursive call to load the association.
                    //Object association = DbObjectReader.readObjectFromDb(associationId, dbAttribute, modelObjects, associationConstrain, newAttributePath);
                    //if(association != null)
                    //    dbAttributeContainer.setAttributeValue(objectToFillInto, dbAttribute, association);
                }
                //If this attribute is not an association
                else if (!dbAttribute.isAssociation() || (dbAttribute.isMultiAssociation() && dbAttribute.getAttributeClass().isEnum())) {
                    Object value = null;
                    value = readObjectFromResultSet(dbAttribute, resultSet);
//                    log.debug("Adding to object("+ objectToFillInto +")." + dbAttribute.getAttributeName() + "("+ value +")");
                    if (value != null && !("" + value).equals("null")) {
                        DbStrip dbStripAnnotation = dbAttribute.getAttribute().getDbStripAnnotation();
                        if (value != null && dbStripAnnotation != null && dbStripAnnotation.urlEncode()) {
                            try {
                                value = URLDecoder.decode((String) value, "UTF-8");
                            } catch (UnsupportedEncodingException e) { }
                        }
                        if (dbAttributeContainer.setAttributeValue(objectToFillInto, dbAttribute, value)) {
                            //log.debug("Read attribute="+attributeName);
                        } else {
                            log.error("Failed at attribute=" + attributeName + " " + value + " " + value.getClass() + " on " + objectToFillInto + " with " + dbAttribute);
                            //return false;
                        }
                    }
                }
            }
            return true;
            //}
//             else
//                 log.warn("The requested object was not in the database " + dbAttributeContainer.getTableName()+" "+objectId);
        } catch (Exception e) {
            log.warn("Something went wrong when reading the result of a select statement " + e, e);
        }
        return false;
    }

    private static Object readObjectFromResultSet(DbAttribute dbAttribute, ResultSet resultSet) throws java.sql.SQLException {
        Object value = null;

        String name = dbAttribute.getInlineAttributeName() != null ? dbAttribute.getInlineAttributeName() : dbAttribute.getAttributeName();
//        int attributeIndex = resultSet.findColumn(dbAttribute.getAttributeName()); //dbAttribute.getColumnIndex();
//        if (attributeIndex == -1) {
//            attributeIndex = resultSet.findColumn(dbAttribute.getAttributeName());
//            dbAttribute.setColumnIndex(attributeIndex);
//        }
        try {

            if(dbAttribute.isMultiAssociation() && dbAttribute.getAttributeClass().isEnum()){
                String s = resultSet.getString(name);
                if(s == null){
                    return null;
                } else {
                    List result = new ArrayList();
                    StringTokenizer toks = new StringTokenizer(s, " ,[]");
                    for(; toks.hasMoreTokens() ;){
                        result.add(Enum.valueOf(dbAttribute.getAttributeClass(), toks.nextToken()));
                    }
                    return result.toArray((Enum[]) java.lang.reflect.Array.newInstance(
                            dbAttribute.getAttributeClass(),
                            result.size()));

                }
            }



            //Convert the data type to the class type.
            switch (dbAttribute.getDataType().getType()) {
                case DbDataType.DB_CLOB:
                case DbDataType.DB_CHAR:
                case DbDataType.DB_VARCHAR:
                    value = resultSet.getString(name);
                    break;
                case DbDataType.DB_INT:
                    value = new Integer(resultSet.getInt(name));
                    break;
                case DbDataType.DB_LONG:
                    value = new Long(resultSet.getLong(name));
                    break;
                case DbDataType.DB_DOUBLE:
                    value = new Double(resultSet.getDouble(name));
                    break;
                case DbDataType.DB_FLOAT:
                    value = new Float(resultSet.getDouble(name));
                    break;
                case DbDataType.DB_BOOLEAN:
                    value = new Boolean(resultSet.getInt(name) == 1);
                    break;
                case DbDataType.DB_DATE:
                    try {
                        String strValue = resultSet.getString(name);
                        if (strValue != null) {
                            if (!strValue.equals("0000-00-00")) {
                                try {
                                    if (resultSet.getTime(name) != null) {
                                        if (resultSet.getDate(name) != null) {
                                            Calendar time = Calendar.getInstance();
                                            Calendar date = Calendar.getInstance();

                                            time.setTime(resultSet.getTime(name));
                                            date.setTime(resultSet.getDate(name));
                                            date.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY));
                                            date.set(Calendar.MINUTE, time.get(Calendar.MINUTE));
                                            date.set(Calendar.SECOND, time.get(Calendar.SECOND));
                                            //log.debug("*** Time " + name + ":" + date.getTime());
                                            return date;
                                        }
                                    }
                                } catch (java.sql.SQLException msg){
                                    if(msg.toString().contains("Bad format for Time")){
                                        final Date parse = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(strValue);
                                        Calendar c = Calendar.getInstance();
                                        c.setTime(parse);
                                        return c;
                                    } else {
                                        throw  msg;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        if((e.toString().indexOf("0000-00-00") == -1)){
                        log.error("resultSet.getString(attribute = " + dbAttribute.getAttributeName() + ")");
                        log.error("some error with name("+ name +") in " + dbAttribute.getTableName() + "", e);
                        }
                    }

                    return null;

// 		    //log.debug("Reading attributeIndex="+ attributeIndex);
// 		    //Calendar time = Calendar.getInstance();
// 		    //Calendar date = Calendar.getInstance();
// 		    try{
// 			//if(resultSet.getTime(attributeIndex) != null && resultSet.getDate(attributeIndex) != null){
// 			//time.setTime(resultSet.getTime(attributeIndex));
// 			//date.setTime(resultSet.getDate(attributeIndex));
// 			//date.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY));
// 			//date.set(Calendar.MINUTE, time.get(Calendar.MINUTE));
// 			//date.set(Calendar.SECOND, time.get(Calendar.SECOND));
// 			    Calendar cNew = Calendar.getInstance();
// 			    Timestamp d = resultSet.getTimestamp(attributeIndex, cNew);
// 			    if(d == null) return null;
// 			    cNew.setTime(d);
// 			    //log.debug("readObjectFromResultSet before("+ date.getTime() +") after("+ cNew.getTime()  +")");
// 			    return cNew;
// 			    //value = cNew;
// 			    //} else {
// 			    //value = null;
// 			    //}
// 			    //break;
// 		    } catch(Exception e){
// 			log.warn("readObjectFromResultSet: "+ e, e);
// 			return null;
// 		    }

            }
        } catch (Exception e) {
            log.warn("Attribute conversion error", e);
            //return false;
        }
        return value;
    }

    /**
     * This method will load all the multi associations/primitive arrays which the object has.
     *
     * @param object               The object which has the multi associations.
     * @param dbAttributeContainer The attribute container of the object.
     * @param modelObjects         The object which we have allready loaded in the request.
     * @param associationConstrain The associations which may be loaded or not.
     * @param attributePath        The concatenated attributePath so far.

    private static void loadMultiAssociations(ModelObject object, DbAttributeContainer dbAttributeContainer, Map modelObjects, AssociationConstrain associationConstrain, String attributePath, boolean cache) {
        //Find the multi associations.
        for (Iterator iterator = dbAttributeContainer.getDbAttributes().values().iterator(); iterator.hasNext();) {
            DbAttribute dbAttribute = (DbAttribute) iterator.next();
            if (dbAttribute.isMultiAssociation()) {
                //This is a multi association.
                String attributeName = dbAttribute.getAttributeName();

                String newAttributePath = AssociationConstrain.addAttributeToPath(attributePath, attributeName);
                if (associationConstrain.isAllowedAssociation(newAttributePath)) {
                    //We may load the association.
                    Object value = getMultiAssociation(object, dbAttributeContainer, dbAttribute, modelObjects, associationConstrain, attributePath, cache);
                    if (value == null) {
                        log.error("Got multi association which was null " + dbAttribute.getClassName());
                    }
                    dbAttributeContainer.setAttributeValue(object, dbAttribute, value);
                } else {
                    object.setCachable(false);
                }
            }
        }
    }
    */
    /**
     * This method loads a multi association or an primitive array.
     *
     * @param object                     The object to load into.
     * @param sourceDbAttributeContainer The attribute container of the object.
     * @param dbAttribute                The multi association attribute.
     * @param modelObjects               The objects which has been read.
     * @param associationConstrain       The associations which may be loaded or not.
     * @param attributePath              The concatenated attributePath so far.
     */
    public static Object getMultiAssociation(ModelObject object, DbAttributeContainer sourceDbAttributeContainer, DbAttribute dbAttribute, Map modelObjects, AssociationConstrain associationConstrain, String attributePath, boolean cache) {

        String attributeName = dbAttribute.getAttributeName();

        //log.debug("dbAttribute.getAttributeClass() = " + dbAttribute.getAttributeClass());

        if (!dbAttribute.isPrimitivArrayAssociation()) {
            //Check to see if the associated class exists.
            DbAttributeContainer targetDbAttributeContainer = DbClassReflector.getDbAttributeContainer(dbAttribute);
            if (targetDbAttributeContainer != null) {
                //Make select sql statement, to select the required tupels from the association table.
                String sourceTableName = sourceDbAttributeContainer.getTableName();
                String targetTableName = targetDbAttributeContainer.getTableName();
                SelectSQLStatement selectSQLStatement = SQLStatementFactory.getSelectSQLStatement();
                selectSQLStatement.addTableName(AssociationTable.makeAssociationTableName(sourceDbAttributeContainer, dbAttribute));
                selectSQLStatement.addAttributeName(sourceDbAttributeContainer.getAttributeContainer().getClassName() + "_" + sourceDbAttributeContainer.getPrimaryKeyAttribute().getAttributeName()); //AssociationTable.SOURCE);
                selectSQLStatement.addAttributeName(targetDbAttributeContainer.getPrimaryKeyAttribute().getAttributeName());//AssociationTable.TARGET);
                selectSQLStatement.addConstrain(AssociationTable.makeAssociationTableName(sourceDbAttributeContainer, dbAttribute) + "." + sourceDbAttributeContainer.getAttributeContainer().getClassName() + "_" + sourceDbAttributeContainer.getPrimaryKeyAttribute().getAttributeName(), WhereSQLStatement.EQUAL, object.getPrimaryKeyValue());

                LimResultSet limSet = null;
                try {
                    limSet = SQLStatementExecutor.doQuery(selectSQLStatement);
                    ResultSet associationResultSet = limSet.getResultSet();
                    if (associationResultSet != null) {

                    //Make a new request for each association.
                    List associationObjects = new ArrayList();
                    while (associationResultSet.next()) {
                        String associationId = associationResultSet.getString(/*AssociationTable.TARGET*/targetDbAttributeContainer.getPrimaryKeyAttribute().getAttributeName());
//                        log.debug("getMultiAssociation::associationId = " + associationId + " associationId == null ? " + (associationId == null));
                        //Load the association. Recursive call.
                        Object association = DbObjectReader.readObjectFromDb(associationId, dbAttribute.getAttributeClass(), modelObjects, associationConstrain, attributePath, cache);
                        if (association != null) {
                            associationObjects.add(association);
                        }
                    }
                    //Make an array out of the objects.

                    Object[] associationArray = (Object[]) java.lang.reflect.Array.newInstance(dbAttribute.getAttributeClass(), associationObjects.size());
                    Iterator associationArrayIterator = associationObjects.iterator();
                    for (int i = 0; associationArrayIterator.hasNext(); i++) {
                        associationArray[i] = associationArrayIterator.next();
                    }

                    //Return the association array.
                    return associationArray;
                    } else {
                        log.warn("Result set was null");
                    }

                } catch (Exception e) {
                    log.error("Exception in getMultiAssociation " + e);
                    e.printStackTrace();
                } finally {
                    try {
                        if(limSet != null) limSet.close();
                    } catch (Exception e) {
                        log.error("error.close() = " + e);
                        e.printStackTrace();
                    }
                    limSet = null;
                }
            } else {
                log.error("The associated class " + dbAttribute.getClassName() + " was not a model object");
            }
        } else {
            //This is an array of primitives.
            SelectSQLStatement selectSQLStatement = SQLStatementFactory.getSelectSQLStatement();
            selectSQLStatement.addTableName(AssociationTable.makeAssociationTableName(sourceDbAttributeContainer, dbAttribute));

            selectSQLStatement.addAttributeName(sourceDbAttributeContainer.getAttributeContainer().getClassName() + "_" + sourceDbAttributeContainer.getPrimaryKeyAttribute().getAttributeName());
            selectSQLStatement.addAttributeName(dbAttribute.getAttributeName());
            selectSQLStatement.addConstrain(sourceDbAttributeContainer.getAttributeContainer().getClassName() + "_" + sourceDbAttributeContainer.getPrimaryKeyAttribute().getAttributeName(), WhereSQLStatement.EQUAL, object.getPrimaryKeyValue());

            LimResultSet limSet = null;
            try {
                limSet = SQLStatementExecutor.doQuery(selectSQLStatement);
                ResultSet associationResultSet = limSet.getResultSet();
                if (associationResultSet != null) {
                    List associationObjects = new ArrayList();
                    //
                    while (associationResultSet.next()) {
                        Object value = readObjectFromResultSet(dbAttribute, associationResultSet);
                        if (value != null) {
                            associationObjects.add(value);
                        }
                    }
                    //Make an array out of the objects.
                    Object[] associationArray = (Object[]) java.lang.reflect.Array.newInstance(dbAttribute.getAttributeClass(), associationObjects.size());
                    Iterator associationArrayIterator = associationObjects.iterator();
                    for (int i = 0; associationArrayIterator.hasNext(); i++) {
                        associationArray[i] = associationArrayIterator.next();
                    }
                    return associationArray;
                }
            } catch (Exception e) {
                log.error("Error while reading", e);
            } finally {
                try {
                    if (limSet != null) {
                        limSet.close();
                    }
                } catch (Exception e) {
                    log.error("error.close() = " + e);
                    e.printStackTrace();
                }
                limSet = null;
            }

        }
        return null;
    }
}
