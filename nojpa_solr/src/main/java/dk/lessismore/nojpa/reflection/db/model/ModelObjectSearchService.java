package dk.lessismore.nojpa.reflection.db.model;

import dk.lessismore.nojpa.db.DbDataType;
import dk.lessismore.nojpa.reflection.attributeconverters.AttributeConverter;
import dk.lessismore.nojpa.reflection.attributeconverters.AttributeConverterFactory;
import dk.lessismore.nojpa.reflection.db.DbClassReflector;
import dk.lessismore.nojpa.reflection.db.DbObjectVisitor;
import dk.lessismore.nojpa.reflection.db.annotations.SearchField;
import dk.lessismore.nojpa.reflection.db.attributes.DbAttribute;
import dk.lessismore.nojpa.reflection.db.attributes.DbAttributeContainer;
import dk.lessismore.nojpa.reflection.db.model.nosql.NoSQLInputDocument;
import dk.lessismore.nojpa.reflection.db.model.nosql.NoSQLService;
import dk.lessismore.nojpa.reflection.translate.TranslateModelService;
import dk.lessismore.nojpa.reflection.translate.TranslateModelServiceFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created : with IntelliJ IDEA.
 * User: seb
 */
public class ModelObjectSearchService {

    private static final Logger log = LoggerFactory.getLogger(ModelObjectSearchService.class);


    public static int AUTO_COMMIT_MS = 300;
    public static int INPUTS_BETWEEN_COMMITS_VISITOR = 1000;

    public static boolean trace = false;


    private static HashMap<String, NoSQLService> noSQLServices = new HashMap<>();

    public static void addNoSQLServer(Class className, NoSQLService nosqlServer){
        log.info("Adding nosqlServer("+ nosqlServer +") for class("+ className.getSimpleName() +")");
        noSQLServices.put(className.getSimpleName(), nosqlServer);
    }

//    public static void addSolrServer(Class className, SolrClient solrServer){
//        log.info("Adding solrServer("+ solrServer +") for class("+ className.getSimpleName() +")");
//        SolrService solrService = SolrServiceImpl.getSolrClientWrapper(solrServer);
//        addNoSQLServer(className, solrService);
//    }
//
//    public static void addSolrServer(Class className, SolrService solrServer){
//        log.info("Adding solrServer("+ solrServer +") for class("+ className.getSimpleName() +")");
//        noSQLServices.put(className.getSimpleName(), solrServer);
//    }

    public static <T extends ModelObjectInterface> void deleteAll(T object) {
        ModelObject modelObject = (ModelObject) object;
        deleteAll(modelObject.getInterface());

    }

    private static String serverKey(Class aClass) {
        return aClass.getSimpleName();
    }

    public static <T extends ModelObjectInterface> String serverKey(T object) {
        ModelObject modelObject = (ModelObject) object;
        return modelObject.getInterface().getSimpleName();
    }


    public static <T extends ModelObjectInterface> Class<? extends ModelObjectInterface> getInterfaceClass(T object) {
        ModelObject modelObject = (ModelObject) object;
        return modelObject.getInterface();
    }

    public static void deleteAll(Class aClass) {
        String key = serverKey(aClass);
        try {
            noSQLServices.get(key).deleteAll();
        } catch (Exception e) {
            log.error("Some ERROR-1 when deleting all: " + e, e);
            throw new RuntimeException(e);
        }
    }

    public static <T extends ModelObjectInterface> void delete(T object) {
        String key = serverKey(object);
        try {
            NoSQLService noSQLService = noSQLServices.get(key);
            NoSQLInputDocument inDoc = noSQLService.createInputDocument(getInterfaceClass(object), object);
            addAttributesToDocument(object, "", new HashMap<>(), key, inDoc);
            noSQLServices.get(key).delete(object.getObjectID(), inDoc.getShard());
        } catch (Exception e) {
            log.error("Some error when adding document ... " + e, e);
        }
    }


    public static <T extends ModelObjectInterface> NoSQLService noSQLService(Class<T> aClass) {
        return noSQLServices.get(aClass.getSimpleName());
    }

    public static <T extends ModelObjectInterface> void put(T object, String postfixShardName) {
        try{
            ModelObject modelObject = (ModelObject) object;

            String key = serverKey(object);
            if (!noSQLServices.containsKey(key)) {
                throw new RuntimeException("Cant find a noSQLServices for class("+ modelObject.getInterface().getSimpleName() +")");
            }
            NoSQLService noSQLService = noSQLServices.get(key);
            Class<T> modelClass = (Class<T>)getInterfaceClass(object);
            NoSQLInputDocument inDoc = noSQLService.createInputDocument(modelClass, object);
            inDoc.addPostfixShardName(postfixShardName);
            addAttributesToDocument(object, "", new HashMap<>(), key, inDoc);
            try {
                noSQLService.index(inDoc);
            } catch (Exception e) {
                log.error("Some io error when adding document ... " + e, e);
            }
//          $class-objectID-attribute
//          NQL.getListFromNoSQL(xxx) - no cache ...
//            // translation related
            TranslateModelService<T> translateModelService = TranslateModelServiceFactory.<T>getInstance(modelClass);
            if (translateModelService != null) {
                String from = translateModelService.getSourceLanguage(object);
                List<String> languages = translateModelService.getLanguages();
                for (String language : languages) {
//                    translateModelService.translateSingle(object, translated, from, language);
                    NoSQLInputDocument translatedDoc = noSQLService.createInputDocument(getInterfaceClass(object), object);
                    T translatedObjectOrNull = translateModelService.getTranslatedObjectOrNull(object, language);
                    if (translatedObjectOrNull == null) {
                        addAttributesToDocument(object, "", new HashMap<>(), key, translatedDoc, translateModelService, from, language);
                    } else {
                        addAttributesToDocument(translatedObjectOrNull, "", new HashMap<>(), key, translatedDoc);
                        Set<String> translateFields = inDoc.getTranslateFields();
                        Set<String> fieldNames = inDoc.getAllFields();
                        for(String f : fieldNames) { // _Product_inclusions__TXT_ARRAY_ProductFeature_description__ID_ProductFeatureDescription_content__TXT_ARRAY
                            if (!(translateFields.contains(f) || translateFields.contains(StringUtils.removeEnd(f, "_ARRAY")))) {
                                translatedDoc.setField(f, inDoc.getValue(f));
                            }
                        }
                    }
                    translatedDoc.addPostfixShardName(postfixShardName);
                    translatedDoc.addShard(language);
                    noSQLService.index(translatedDoc);
                    translateModelService.finish(object, translatedObjectOrNull, translatedDoc, language);
                }
            }
        } catch (Exception e){
            log.error("put:_ Some error in put-1 " + e, e);
            throw new RuntimeException(e);
        }
    }

    public static <T extends ModelObjectInterface> void put(T object) {
        put(object, null);
    }

    public static <T extends ModelObjectInterface> void putWithoutCommit(T object) {
        try{
            log.info("Adding (without commit) (" + object.getInterface().getSimpleName() + ")[" + object + "]");
            try{
                if(trace){
                    FileWriter fileWriter = new FileWriter("/tmp/trace-ModelObjectSearchService.log", true);
                    PrintWriter pw = new PrintWriter(fileWriter);
                    new Exception("DEBUG-TRACE").printStackTrace(pw);
                    pw.flush();
                    pw.close();
                    fileWriter.close();
                }
            } catch (Exception e){
                log.error("Can't trace the puts...  " + e, e);
            }

            String key = serverKey(object);
            if (!noSQLServices.containsKey(key)) {
                throw new RuntimeException("Cant find a noSQLServices for class("+key +")");
            }
            NoSQLService noSQLService = noSQLServices.get(key);
            NoSQLInputDocument inDoc = noSQLService.createInputDocument(getInterfaceClass(object), object);
            addAttributesToDocument(object, "", new HashMap<>(), key, inDoc);
            try {
                //TODO: noSQLService.index(inDoc, AUTO_COMMIT_MS);
                noSQLService.index(inDoc);
            } catch (Exception e) {
                log.error("Some io error when adding document ... " + e, e);
            }
        } catch (Exception e){
            log.error("put:_ Some error in put-1 " + e, e);
            throw new RuntimeException(e);

        }
    }

    public static <T extends ModelObjectInterface> void put(T object, String prefix, HashMap<String, String> storedObjects, String key, NoSQLInputDocument solrObj) {

        addAttributesToDocument(object, prefix, storedObjects, key, solrObj);
        try {
              noSQLServices.get(key).index(solrObj);
        } catch (Exception e) {
            log.error("Some error when adding document ... " + e, e);
        }
    }

//    // TODO try not to use this
//    @Deprecated
//    public static <T extends ModelObjectInterface> void put(T object, String prefix, HashMap<String, String> storedObjects, SolrClient solrServer, NoSQLInputDocument solrObj) {
//
//        String key = null;
//        for (String serverKey : servers.keySet()) {
//            if (servers.get(serverKey).equals(solrServer)) {
//                key = serverKey;
//                break;
//            }
//        }
//        if (key == null) {
//            log.error("cannot find proper key for server: " + solrServer);
//            return;
//        }
//        addAttributesToDocument(object, prefix, storedObjects, key, solrObj);
//        try {
//            solrServer.add(solrObj, AUTO_COMMIT_MS);
//        } catch (SolrServerException e) {
//            log.error("Some solr error when adding document ... " + e, e);
//        } catch (IOException e) {
//            log.error("Some io error when adding document ... " + e, e);
//        }
//    }

    private static String storedObjectsKey(String prefix, String objectID, DbAttribute dbAttribute){
        return dbAttribute.getAttributeName() + ":" + objectID;

    }

    private static <T extends ModelObjectInterface> void addAttributesToDocument(T object, String prefix, HashMap<String, String> storedObjects, String key, NoSQLInputDocument inputDocument) {
        addAttributesToDocument(object, prefix, storedObjects, key, inputDocument, null, null, null);
    }

    private static <T extends ModelObjectInterface> void addAttributesToDocument(T object, String prefix, HashMap<String, String> storedObjects, String key, NoSQLInputDocument inputDocument,  TranslateModelService translateModelService, String fromLang, String toLang) {
        //log.debug("addAttributesToDocument:X0");
        ModelObject modelObject = (ModelObject) object;
        DbAttributeContainer dbAttributeContainer = DbClassReflector.getDbAttributeContainer(modelObject.getInterface());
        String objectIDVarName = (prefix.length() == 0 ? "" : prefix + "_") + "objectID" + (prefix.length() == 0 ? "" : "__ID");
        if(prefix.length() == 0) {
            log.trace("Adding solr-row: objectIDVarName(" + objectIDVarName + ")->" + object.getObjectID());
            inputDocument.addField(objectIDVarName, object.getObjectID());
        }
        //log.debug("addAttributesToDocument:X1");
        for (Iterator iterator = dbAttributeContainer.getDbAttributes().values().iterator(); iterator.hasNext();) {
            //log.debug("addAttributesToDocument:X2");
            DbAttribute dbAttribute = (DbAttribute) iterator.next();
            SearchField searchField = dbAttribute.getAttribute().getAnnotation(SearchField.class);
            if(searchField != null && dbAttribute.getInlineAttributeName() == null) {
                //log.debug("addAttributesToDocument:X3");
                if(dbAttribute.getAttribute().getSearchShardAnnotation() != null){
                    Object value = dbAttributeContainer.getAttributeValue(modelObject, dbAttribute);
                    inputDocument.addShard("" + value);
                }



                if(!dbAttribute.isAssociation()) {
                    //log.debug("addAttributesToDocument:X4");
                    Object value = null;
                    value = dbAttributeContainer.getAttributeValue(modelObject, dbAttribute);
                    //log.debug("addAttributesToDocument:X5");
                    if (dbAttribute.isLocation() && value != null) {
                        Object lgn = null;
                        if (dbAttributeContainer.getDbAttributes().get("longitude") != null) {
                            lgn = dbAttributeContainer.getAttributeValue(modelObject, dbAttributeContainer.getDbAttributes().get("longitude"));
                        } else if (dbAttributeContainer.getDbAttributes().get("lng") != null) {
                            lgn = dbAttributeContainer.getAttributeValue(modelObject, dbAttributeContainer.getDbAttributes().get("longitude"));
                        } else {
                            throw new RuntimeException("Can't find a attribute with name longitude or lng ");
                        }
                        String newValue = value + "," + lgn;

                        addAttributeValueToStatement(object, dbAttribute, inputDocument, newValue, prefix, translateModelService, fromLang, toLang);
                        addAttributeValueToStatement(object, dbAttribute, inputDocument, ""+ value +","+ lgn +"", prefix, dbAttribute.getSolrAttributeName(prefix) + "__LOC_RPT", translateModelService, fromLang, toLang);
                    } else {
                        if (searchField.translate()) {
                            inputDocument.addTranslatedFieldName(dbAttribute.getSolrAttributeName(prefix));
                        }
                        addAttributeValueToStatement(object, dbAttribute, inputDocument, value, prefix, translateModelService, fromLang, toLang);
                    }
                    //log.debug("addAttributesToDocument:X6");
                } else if (!dbAttribute.isMultiAssociation()) {
                    //log.debug("addAttributesToDocument:X7");
                    ModelObjectInterface value = (ModelObjectInterface) dbAttributeContainer.getAttributeValue(modelObject, dbAttribute);
                    //log.debug("addAttributesToDocument:X8");
                    addAttributeValueToStatement(object, dbAttribute, inputDocument, value, prefix, translateModelService, fromLang, toLang);
                    //log.debug("addAttributesToDocument:X9");
                    if(value != null && !storedObjects.containsKey(storedObjectsKey(prefix, value.getObjectID(), dbAttribute))){
//                    if(value != null && !storedObjects.containsKey(((ModelObject) value).getInterface() + ":" + value.getObjectID())){
                        //log.debug("addAttributesToDocument:X10");
                        storedObjects.put(storedObjectsKey(prefix, value.getObjectID(), dbAttribute), value.getObjectID());
                        addAttributesToDocument(value, dbAttribute.getSolrAttributeName(prefix), storedObjects, key, inputDocument);
                        //log.debug("addAttributesToDocument:X11");
//TODO: Don't think this is needed...  Was making too m  put(value, dbAttribute.getSolrAttributeName(prefix), storedObjects, key, inputDocument);
                        //log.debug("addAttributesToDocument:X12");
                    }
//                    inputDocument.addField(attributeName, modelObject.getSingleAssociationID(attributeName));
                } else { //isMultiAssociation
                    if(dbAttribute.getAttributeClass().isEnum() || dbAttribute.getAttributeClass().isPrimitive()){
                        throw new RuntimeException("This is not implemented ... and should not be used... ");
//                        Object objects = dbAttributeContainer.getAttributeValue(modelObject, dbAttribute);
//                        String solrAttributeName = dbAttribute.getSolrAttributeName(prefix);
//                        inputDocument.addField(solrAttributeName, objects);

                    } else {
                        //log.debug("addAttributesToDocument:X15");
                        ModelObjectInterface[] vs = (ModelObjectInterface[]) dbAttributeContainer.getAttributeValue(modelObject, dbAttribute);
                        HashMap<String, ArrayList<Object>> values = new HashMap<String, ArrayList<Object>>();
                        for(int i = 0; vs != null && i < vs.length; i++){
                            ModelObjectInterface value = vs[i];
                            if(value != null && !storedObjects.containsKey(storedObjectsKey(prefix, value.getObjectID(), dbAttribute))){
                                storedObjects.put(storedObjectsKey(prefix, value.getObjectID(), dbAttribute), value.getObjectID());
                                getSearchValues(value, dbAttribute.getSolrAttributeName(prefix), storedObjects, values, inputDocument);
                            }
                        }
                        Iterator<String> nameIterator = values.keySet().iterator();
                        for(int i = 0; nameIterator.hasNext(); i++){
                            String name = nameIterator.next();
                            ArrayList<Object> objects = values.get(name);
                            String solrArrayName = name + "_ARRAY";
                            log.trace("Adding_to_array.size " + solrArrayName + "("+ (objects == null ? "-1" : (objects.size() == 1 ? ""+ objects.get(0) : ""+ objects.size())) +")");
                            if(!solrArrayName.contains("creationDate")) {
                                inputDocument.addField(solrArrayName, objects);
                            }
                        }
                        //log.debug("addAttributesToDocument:X16");
                    }
                }
            }
        }
    }

    private static  <T extends ModelObjectInterface> void getSearchValues(T object, String prefix, HashMap<String, String> storedObjects, HashMap<String, ArrayList<Object>> values, NoSQLInputDocument inputDocument){
        ModelObject modelObject = (ModelObject) object;
        DbAttributeContainer dbAttributeContainer = DbClassReflector.getDbAttributeContainer(modelObject.getInterface());
        String objectIDInSolr = (prefix.length() == 0 ? "" : prefix + "_") + "objectID" + (prefix.length() == 0 ? "" : "__ID");
        for (Iterator iterator = dbAttributeContainer.getDbAttributes().values().iterator(); iterator.hasNext();) {
            DbAttribute dbAttribute = (DbAttribute) iterator.next();
            String attributeName = dbAttribute.getAttributeName();
            if(attributeName.equals("objectID")){
                addAttributeValueToMap(dbAttribute, "" + object, prefix, values);
            }


            SearchField searchField = dbAttribute.getAttribute().getAnnotation(SearchField.class);
//            log.debug("getSearchValues:"+ object +":attributeName:" + attributeName + " with values("+ (values != null ? values.size() : -1) +")");
            if(searchField != null) {
                if(!dbAttribute.isAssociation()) {
                    Object value = null;
                    value = dbAttributeContainer.getAttributeValue(modelObject, dbAttribute);
                    if(value != null){
                        if (searchField.translate()) {
                            inputDocument.addTranslatedFieldName(dbAttribute.getSolrAttributeName(prefix));
                        }
                        addAttributeValueToMap(dbAttribute, value, prefix, values);
                    }
                } else if (!dbAttribute.isMultiAssociation()) {
                    ModelObjectInterface value = (ModelObjectInterface) dbAttributeContainer.getAttributeValue(modelObject, dbAttribute);
                    if(value != null && !storedObjects.containsKey(storedObjectsKey(prefix, value.getObjectID(), dbAttribute))){
                        storedObjects.put(storedObjectsKey(prefix, value.getObjectID(), dbAttribute), value.getObjectID());
                        getSearchValues(value, dbAttribute.getSolrAttributeName(prefix), storedObjects, values, inputDocument);
                    }
                } else {
                    ModelObjectInterface[] vs = (ModelObjectInterface[]) dbAttributeContainer.getAttributeValue(modelObject, dbAttribute);
                    for(int i = 0; vs != null && i < vs.length; i++){
                        ModelObjectInterface value = vs[i];
                        if(value != null && !storedObjects.containsKey(storedObjectsKey(prefix, value.getObjectID(), dbAttribute))){
                            storedObjects.put(storedObjectsKey(prefix, value.getObjectID(), dbAttribute), value.getObjectID());
                            getSearchValues(value, dbAttribute.getSolrAttributeName(prefix), storedObjects, values, inputDocument);
                        }
                    }
                }
            }
        }

    }

    private static  <T extends ModelObjectInterface> void addAttributeValueToMap(DbAttribute dbAttribute, Object value, String prefix, HashMap<String, ArrayList<Object>> values){
        String solrAttributeName = dbAttribute.getSolrAttributeName(prefix);
//        log.debug("Will add solrAttributeName("+ solrAttributeName +") with value("+ value +") to objectMap");
        if (value != null) {
            ArrayList<Object> objects = values.get(solrAttributeName);
            if(objects == null){
                objects = new ArrayList<Object>();
                values.put(solrAttributeName, objects);
            }
            if(dbAttribute.getDataType().getType() == DbDataType.DB_DATE){
                //log.debug("***TimeWrite: " + attributeName + " " + (value != null ? ((Calendar) value).getTime() : "null"));
                SimpleDateFormat xmlDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); //2011-11-28T18:30:30Z
                xmlDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                value = xmlDateFormat.format(((Calendar) value).getTime());
            }

            objects.add(value);
        }
    }





    public static DbObjectVisitor putAll(){
        log.debug("************* putAll() *****************");
        return putAll(INPUTS_BETWEEN_COMMITS_VISITOR);
    }

    public static DbObjectVisitor putAll(final int inputsBetweenCommits){
        DbObjectVisitor dbObjectVisitor = new DbObjectVisitor() {
            int counter = 0;


            //TODO:SEB This can be optimized !!
            @Override
            public void visit(ModelObjectInterface m) {
                putWithoutCommit(m);
                if(counter++ % inputsBetweenCommits == 0){
                    ModelObjectSearchService.commit(m);
                }
            }

            @Override
            public void setDone(boolean b) {

            }

            @Override
            public boolean getDone() {
                return false;
            }
        };
        return dbObjectVisitor;
    }



    public static <T extends ModelObjectInterface> void commit(T object) {
        try {
            noSQLServices.get(serverKey(object)).commit();
        } catch (Exception e) {
            log.error("Some error when commit document ... " + e, e);
        }
    }

    public static <T extends ModelObjectInterface> void commit(Class<T> modelObjectClass) {

        try {
            noSQLServices.get(serverKey(modelObjectClass)).commit();
        } catch (Exception e) {
            log.error("Some error when commit document ... " + e, e);
        }
    }


    private static void addAttributeValueToStatement(ModelObjectInterface object, DbAttribute dbAttribute, NoSQLInputDocument solrObj, Object value, String prefix, TranslateModelService translateModelService, String fromLang, String toLang) {
        String solrAttributeName = dbAttribute.getSolrAttributeName(prefix);
//        value =
        addAttributeValueToStatement(object, dbAttribute, solrObj, value, prefix, solrAttributeName, translateModelService, fromLang, toLang);
    }

    private static void addAttributeValueToStatement(ModelObjectInterface object, DbAttribute dbAttribute, NoSQLInputDocument solrObj, Object value, String prefix, String solrAttributeName, TranslateModelService translateModelService, String fromLang, String toLang) {
        if(value != null && value instanceof Calendar){
            log.trace("Will add solrAttributeName(" + solrAttributeName + ") with value(" + ((Calendar) value).getTime() + ")");
        } else {
            log.trace("Will add solrAttributeName("+ solrAttributeName +") with value("+ value +")");
        }

        if (value != null) {
            //Convert the value to the equivalent data type.

            int type = dbAttribute.getDataType().getType();
            switch (type) {
                case DbDataType.DB_LONG:
                    solrObj.addField(solrAttributeName, ((Long) value).longValue());
                    break;
                case DbDataType.DB_CHAR:
                case DbDataType.DB_VARCHAR:
                    String valueStr = null;
                    if (value instanceof String) {
                        if (translateModelService != null && dbAttribute.getAttribute().getSearchFieldAnnotation().translate() && !fromLang.equals(toLang)) {
                            Class modelClass = (Class)getInterfaceClass(object);
                            valueStr = translateModelService.translate(modelClass, object, dbAttribute.getAttributeName(), (String) value, fromLang, toLang);
                        } else {
                            valueStr = (String) value;
                        }

                    } else {
                        //This attribute is not a string; but we have to save it as one in the
                        //database. We must convert the object to a string!
                        AttributeConverter converter = AttributeConverterFactory.getInstance().getConverter(dbAttribute.getAttributeClass());
                        if (converter != null) {
                            //We have a converter for it.
                            if (!dbAttribute.getAttribute().isArray()) {
                                valueStr = converter.objectToString(value);
                            } else {
                                valueStr = converter.arrayToString((Object[]) value);
                            }
                        } else {
                            valueStr = value.toString();
                        }
                    }
                    solrObj.addField(solrAttributeName, valueStr);
                    log.debug("addAttributeValueToStatement(): solrObj.addField(" + solrAttributeName +", "+ valueStr +");");
                    break;
                case DbDataType.DB_INT:
                    solrObj.addField(solrAttributeName, ((Integer) value).intValue());
                    break;
                case DbDataType.DB_DOUBLE:
                    solrObj.addField(solrAttributeName, ((Double) value).doubleValue());
                    break;
                case DbDataType.DB_FLOAT:
                    solrObj.addField(solrAttributeName, ((Float) value).floatValue());
                    break;
                case DbDataType.DB_BOOLEAN:
                    solrObj.addField(solrAttributeName, ((Boolean) value).booleanValue() );
                    break;
                case DbDataType.DB_DATE:
                    //log.debug("***TimeWrite: " + attributeName + " " + (value != null ? ((Calendar) value).getTime() : "null"));
                    SimpleDateFormat xmlDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); //2011-11-28T18:30:30Z
                    xmlDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    solrObj.addField(solrAttributeName, xmlDateFormat.format(((Calendar) value).getTime()));
                    break;
            }
        } else {
            if (dbAttribute.getDataType().getType() == DbDataType.DB_DATE) {
                solrObj.addField(solrAttributeName, ((Calendar) value));
            } else {
                solrObj.addField(solrAttributeName, (String) null);
            }
        }

    }








}
