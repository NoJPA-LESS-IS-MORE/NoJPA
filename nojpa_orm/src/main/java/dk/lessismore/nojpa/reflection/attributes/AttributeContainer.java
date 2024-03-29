package dk.lessismore.nojpa.reflection.attributes;

import dk.lessismore.nojpa.reflection.db.annotations.*;
import dk.lessismore.nojpa.reflection.db.model.ModelObject;
import dk.lessismore.nojpa.reflection.db.model.ModelObjectInterface;
import dk.lessismore.nojpa.reflection.util.ClassAnalyser;
import dk.lessismore.nojpa.reflection.visitors.AttributeContainerVisitor;
import dk.lessismore.nojpa.reflection.visitors.AttributeVisitor;
import dk.lessismore.nojpa.reflection.visitors.GetAttributeNamesVisitor;
import dk.lessismore.nojpa.reflection.visitors.GetAttributeValueVisitor;
import dk.lessismore.nojpa.reflection.visitors.GetAttributeVisitor;
import dk.lessismore.nojpa.reflection.visitors.SetAttributeValueVisitor;
import dk.lessismore.nojpa.utils.GenericComparator;
import dk.lessismore.nojpa.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

/**
 * This class can reflect a generic class; and identify the attributes which the class has. It
 * allso works as an container for the relfected fields; so that you can use this class to
 * access the fields. This class can only identify attributes if they have either a get or a set
 * method; or if the attribute is a public field. If a field is not a standard field (string, boolean
 * int etc.) this class will make a recursive call; which makes an new AttributeContainer. You
 * should therefor be carefull about which classes you analyse because you can start a really big
 * analyse process; which may take some time.
 * <br>NB: this works allso for inheritance.
 * <br>When you have made an instance of this class please call the method <tt>findAttributes</tt>;
 * which starts the analysation of the class.
 *
 * @version 1.0 21-5-2
 * @author LESS-IS-MORE
 */
public class AttributeContainer {

    private static final Logger log = LoggerFactory.getLogger(AttributeContainer.class);




    /**
     * The class which the attribute in this container belongs to.
     */
    private Class _targetClass = null;

    private SearchRoute searchRouteAnnotation = null;
    private Attribute searchRouteAnnotationAttribute = null;

    private SearchShard searchShardAnnotation = null;
    private Attribute searchShardAnnotationAttribute = null;

    /**
     * The attributes of the target class. key=attributeName, value=Attribute
     */
    private Map<String, Attribute> _attributes = new HashMap<String, Attribute>();

    public AttributeContainer() { }

    public Class getTargetClass() {
        return _targetClass;
    }

    private boolean containsLob = false;

    public boolean containsLob() {
        return containsLob;
    }

    /**
     * Gets the name of the class which this container is build on. This name does not
     * include the classpath; but only the name ex. "AttributeContainer".
     */
    public String getClassName() {
        return ClassAnalyser.getClassName(getTargetClass());
    }
    /**
     * This method starts the process that identifies the attributes of the class.
     */
    public void findAttributes(ModelObjectInterface target) {
        findAttributes(((ModelObject)target).getInterface());
    }

    /**
     * This method starts the process that identifies the attributes of the class.
     */
    public void findAttributes(Class targetClass) {
        _targetClass = targetClass;
        findAttributesFromMethods(getTargetClass().getMethods());
    }

    protected void findDeclaredAttributes(Class targetClass) {
        _targetClass = targetClass;
        findAttributesFromMethods(getTargetClass().getDeclaredMethods());
    }


    /**
     * This method analyses the class, and findes the attributes in it; from the
     * get and set methods in the class.
     */
    protected void findAttributesFromMethods(Method[] methods) {
        //Get public methods.
//        Method[] methods = getTargetClass().getMethods();
        //Handle get Methods.
        for(int i = 0; i < methods.length; i++) {
//            log.debug(getTargetClass().getName() + ": Current Method: " + methods[i].getName());
            Method method = methods[i];
            if(ClassAnalyser.isValidGetMethod(method)) {
                String attributeName = ClassAnalyser.getAttributeNameFromMethod(method);
                if(attributeName.isEmpty())
                    continue;
                Attribute attribute = (Attribute)getAttributes().get(attributeName);
                MethodAttribute methodAttribute = null;
                if(attribute == null ) {
                    //This attribute has not been made before. We make it.
                    methodAttribute = new MethodAttribute();
                    methodAttribute.setGetMethod(method);
                    if(method.getReturnType().isAnnotationPresent(DbInline.class)){
//                        log.debug("We have DbInline");
                        getAttributes().put(methodAttribute.getAttributeName(), methodAttribute); //Adding the normal attribute

                        AttributeContainer inlineAttributeContainer = new AttributeContainer();
                        inlineAttributeContainer.findDeclaredAttributes(method.getReturnType());
                        Map<String, Attribute> inlineAttributes = inlineAttributeContainer.getAttributes();

                        for(Iterator<Map.Entry<String, Attribute>> iterator = inlineAttributes.entrySet().iterator(); iterator.hasNext(); ){
                            Map.Entry<String, Attribute> inline = iterator.next();

                            Attribute att = inline.getValue();
                            att.setInlineAttributeName(methodAttribute.getAttributeName()+ "_" + att.getAttributeName());
                            att.setInlineParentName(methodAttribute.getAttributeName());
                            att.setInlineParentClass(method.getReturnType());
                            att.setInlineChildName(att.getAttributeName());
                            getAttributes().put(att.getInlineAttributeName(), att);
                        }
                    } else {
                        if(method.isAnnotationPresent(LongTextClob.class)){
                            containsLob = true;
                            methodAttribute.setLongTextClob(true);
                        }
                        getAttributes().put(methodAttribute.getAttributeName(), methodAttribute);
                    }



                } else if(attribute instanceof MethodAttribute) {
                    methodAttribute = (MethodAttribute)attribute;
                    methodAttribute.setGetMethod(method);
                }

                methodAttribute.setDeclaringClass( methods[i].getDeclaringClass() );
                SearchField searchFieldAnnotation = method.getAnnotation(SearchField.class);
                methodAttribute.setSearchFieldAnnotation(searchFieldAnnotation);
                SearchRoute searchRouteAnnotation = method.getAnnotation(SearchRoute.class);
                if(searchRouteAnnotation != null){
                    searchRouteAnnotationAttribute = methodAttribute;
                }
                methodAttribute.setSearchRouteAnnotation(searchRouteAnnotation);

                SearchShard searchShardAnnotation = method.getAnnotation(SearchShard.class);
                if(searchShardAnnotation != null){
                    searchShardAnnotationAttribute = methodAttribute;
                    this.searchShardAnnotation = searchShardAnnotation;
                }
                methodAttribute.setSearchShardAnnotation(searchShardAnnotation);

                if((method.getName().startsWith("get") && method.getParameterTypes().length == 1 && method.getParameterTypes()[0].equals(Locale.class))){
                    methodAttribute.setTranslatedAssociation( true );
                }


            }
        }
        //Handle set methods.
        for(int i = 0; i < methods.length; i++) {
	    //log.debug("findAttributesFromMethods:6");
            Method method = methods[i];
            if(ClassAnalyser.isValidSetMethod(method)) {
                String attributeName = ClassAnalyser.getAttributeNameFromMethod(method);
                if(attributeName.isEmpty())
                    continue;

                Attribute attribute = (Attribute)getAttributes().get(attributeName);
                if(attribute == null ) {
                    //We have not encountered this attribute before
                    MethodAttribute methodAttribute = new MethodAttribute(getTargetClass());
                    methodAttribute.setSetMethod(method);
                    //log.debug("method = " + method.getName());
                    getAttributes().put(methodAttribute.getAttributeName(), methodAttribute);
                } else if(attribute instanceof MethodAttribute) {
                    //We have encountered this attribute before (maybe as an get method).
                    MethodAttribute methodAttribute = (MethodAttribute)attribute;
                    Method oldSetMethod = methodAttribute.getSetMethod();
                    if(oldSetMethod != null) {
                        //If we allready have an set method we check to see if its
                        //argument class type match that of the get method
                        //If so its a better match and should be used instead.
                        Class methodParameterClass = method.getParameterTypes()[0];
                        Class getMethodReturnClass = methodAttribute.getGetMethod().getReturnType();
                        if(methodParameterClass.getName().equals(getMethodReturnClass.getName()))
                            methodAttribute.setSetMethod(method);
                    } else {
                        methodAttribute.setSetMethod(method);
                    }
                }
                DbStrip dbStripAnnotation = method.getAnnotation(DbStrip.class);
                if(dbStripAnnotation != null){
                    attribute.setDbStripAnnotation(dbStripAnnotation);
                }
            }
        }
        //Remove the method attributes which has different class types for the set and get method.
        final Collection<Attribute> attributeCollection = getAttributes().values();
        Attribute[] atts = attributeCollection.toArray(new Attribute[attributeCollection.size()]);
        for (int i = 0; i < atts.length; i++) {
	    //log.debug("findAttributesFromMethods:8 - loop");
            Attribute attribute = atts[i];
            if(attribute instanceof MethodAttribute) {
                MethodAttribute methodAttribute = (MethodAttribute)attribute;
                Method getMethod = methodAttribute.getGetMethod();
                Method setMethod = methodAttribute.getSetMethod();
                if(getMethod != null && setMethod != null) {
                    if(!getMethod.getReturnType().getName().equals(setMethod.getParameterTypes()[0].getName())) {
                        if(!getMethod.getReturnType().isAssignableFrom(setMethod.getParameterTypes()[0])) {
                            //The set and get method is reflecting a attribute which has
                            //different class types. This is not allowed; because it
                            //will cause some confusion; on which type the attribute is.
                            getAttributes().remove(methodAttribute.getAttributeName());
                            String message = "findAttributesFromMethods: WARNING: " + methodAttribute.getAttributeName() + " will not become a db-attribute, because type not match";
                            log.warn(message, new Exception(message));

                        }
                    }
                } else {
                    getAttributes().remove(methodAttribute.getAttributeName());
                }
            }
        }
    }



    /**
     * Gets the attribute hashtable (key=attributeName, value=Attribute).
     */
    public Map<String, Attribute> getAttributes() {
        return _attributes;
    }

    /**
     * Gets an attribute from the container with the given name.
     * @return The attribute which has the attributeName. If is not found
     * null will be returned.
     */
    public Attribute getAttribute(String attributeName) {
        return (Attribute)getAttributes().get(attributeName);
    }

    /**
     * This is the main entrance to visit the container; with an AttributeContainerVisitor.
     */
    public void visit(AttributeContainerVisitor visitor) {
        visit(visitor, "");
    }
    public void visit(AttributeContainerVisitor visitor, String prefix) {
        visitor.visitContainer(this, prefix );
    }
    public void visit(AttributeVisitor visitor) {
        visit(visitor, "");
    }

    /**
     * We visit all of the attributes in the container.
     */
    public void visit(AttributeVisitor visitor, String prefix) {
        for (Iterator iterator = getAttributes().values().iterator(); iterator.hasNext();) {
            Attribute attribute = (Attribute) iterator.next();
            //if(!attribute.getAttributeClass().getName().equals(getTargetClass().getName())) {
                //This is the loop prevention. If we have encountered the attribute name before
                //this will be present in the prefix.
                if(prefix.indexOf("."+attribute.getAttributeName()) == -1)
                    visitor.visitAttribute(attribute, Attribute.makePrefix(prefix, attribute.getAttributeName()));
            //}
        }
    }

    /**
     * This method can set an attribute value at a given attribute. The attribute must be
     * present and must be writable.
     */
    public boolean setAttributeValue(Object objectToSetOn, String attributeName, Object value) {

        Attribute attribute = getAttribute(attributeName);
        if(attribute == null){
            log.error("We don't know ("+ attributeName +") on " + _targetClass.getSimpleName());
            for(Iterator<String> iterator = _attributes.keySet().iterator(); iterator.hasNext(); ){
                log.info("Attribute on " + _targetClass.getSimpleName() + " is: " + iterator.next());
            }

        }


        if(attribute.getAttributeClass().isEnum() && !attribute.isArray()){
            if(value == null){
                attribute.setAttributeValuePlain(objectToSetOn, null);
            } else {
                try {
                    Enum anEnum = Enum.valueOf(attribute.getAttributeClass(), (String) value);
                    attribute.setAttributeValuePlain(objectToSetOn, anEnum);
                    return true;
                } catch (Exception e) {
                    log.error("can't get an enum value: " + e.getMessage(), e);
                }
//                Object[] enumConstants = attribute.getAttributeClass().getEnumConstants();
//                for(int i = 0; enumConstants != null && i < enumConstants.length; i++){
//                    if(enumConstants[i].toString().equals("" + value)){
//                        attribute.setAttributeValuePlain(objectToSetOn, enumConstants[i]);
//                        return true;
//                    }
//                }
            }
        }

        //log.debug("att: ("+ attribute +") -> " + value);
        return attribute.setAttributeValue(objectToSetOn, value);
    }

    /**
     * This attribute sets an value at an attribute. The method analyses the attributePathName; and
     * determines if this is the last stop on the line. If not; we call recursive down to
     * the next attributeContainer. You can therefor give an attributePathName which is like
     * this <tt>globe.position.x</tt>. This will create 2 recursive calls until we reach the
     * destination and can set the x value.
     */
    public boolean setAttributeValueFromPathName(Object objectToSetOn, String attributePathName, Object value) {
        SetAttributeValueVisitor visitor = new SetAttributeValueVisitor();
        visitor.setAttributePathName(attributePathName);
        visitor.setObjectToSetOn(objectToSetOn);
        visitor.setValue(value);
        this.visit(visitor);
        return visitor.isSuccessfull();
    }


    public Object getAttributeValue(Object objectToGetFrom, String attributeName) {
        Attribute attribute = getAttribute(attributeName);
        return attribute.getAttributeValue(objectToGetFrom);
    }

    /**
     * This method gets an attribute with the desired attributepath name.
     */
    public Attribute getAttributeFromPathName(Object objectToGetFrom, String attributePathName) {
        GetAttributeVisitor visitor = new GetAttributeVisitor();
        visitor.setAttributePathName(attributePathName);
        visitor.setObject(objectToGetFrom);
        this.visit(visitor);
        return visitor.getAttribute();
    }
    public Object getAttributeValueFromPathName(Object objectToGetFrom, String attributePathName) {
        GetAttributeValueVisitor visitor = new GetAttributeValueVisitor();
        visitor.setObjectToGetFrom(objectToGetFrom);
        visitor.setAttributePathName(attributePathName);
        this.visit(visitor);
        return visitor.getValue();
    }
    public String toString() {

        String attributes = "";
        for (Iterator iterator = getAttributes().values().iterator(); iterator.hasNext();) {
           Attribute attribute = (Attribute)iterator.next();
            attributes += attribute+"\n";
        }
        return attributes;
    }

    /**
     * This method will get a list of all of the attribute names recusivly down from this
     * point. The names will be the attributePath Names.
     */
    public Vector getAttributeNamesRecursive() {
        GetAttributeNamesVisitor nameVisitor = new GetAttributeNamesVisitor();
        nameVisitor.setIgnoreNotConvertable(false);
        this.visit(nameVisitor);
        return nameVisitor.getAttributeNames();
    }

    public <T extends Annotation> Attribute[] getAttributesWithAnnotaions(Class<T> annotationsClass, String dotNotationAttNames) {
        Map<String, Attribute> map = getAttributes();
        ArrayList<AttPair> pairs = new ArrayList<AttPair>(map.size());
        for(Iterator<Attribute> attributeIterator = map.values().iterator(); attributeIterator.hasNext(); ){
            Attribute attribute = attributeIterator.next();
            T t = attribute.getAnnotation(annotationsClass);
            if(t != null){
                pairs.add(new AttPair(t , attribute));
            }
        }
        Collections.sort(pairs, new GenericComparator(AttPair.class, "first." + dotNotationAttNames));
        ArrayList<Attribute> toReturn = new ArrayList<Attribute>();
        for(int i = 0; i < pairs.size(); i++){
            toReturn.add(pairs.get(i).getSecond());
        }
        return toReturn.toArray(new Attribute[toReturn.size()]);
    }


    public <T extends Annotation> List<Attribute> getAttributesWithAnnotation(Class<T> annotationsClass) {
        Collection<Attribute> allAttributes = getAttributes().values();
        List<Attribute> selectedAttributes = new ArrayList<Attribute>();
        for (Attribute attribute : allAttributes) {
            T t = attribute.getAnnotation(annotationsClass);
            if (t != null) {
                selectedAttributes.add(attribute);
            }
        }
        return selectedAttributes;
    }
    
    public <T extends Annotation> List<Attribute> getAttributesWithAnnotation(final Class<T> annotationsClass, final Comparator<T> comparator) {
        List<Attribute> attributes = getAttributesWithAnnotation(annotationsClass);
        Comparator<Attribute> attributeComparator = new Comparator<Attribute>() {
            @Override
            public int compare(Attribute attribute1, Attribute attribute2) {
                T viewOrderAnnotation1 = attribute1.getAnnotation(annotationsClass);
                T viewOrderAnnotation2 = attribute2.getAnnotation(annotationsClass);
                return comparator.compare(viewOrderAnnotation1, viewOrderAnnotation2);
            }
        };
        Collections.sort(attributes, attributeComparator);
        return attributes;
    }


    public <T extends Annotation> List<Attribute> getAttributesWithoutAnnotation(Class<T> annotationsClass) {
        Collection<Attribute> allAttributes = getAttributes().values();
        List<Attribute> selectedAttributes = new ArrayList<Attribute>();
        for (Attribute attribute : allAttributes) {
            T t = attribute.getAnnotation(annotationsClass);
            if (t == null) {
                selectedAttributes.add(attribute);
            }
        }
        return selectedAttributes;
    }

    public SearchRoute getSearchRouteAnnotation() {
        return searchRouteAnnotation;
    }
    public SearchShard getSearchShardAnnotation() {
        return searchShardAnnotation;
    }

    public Attribute getSearchRouteAnnotationAttribute() {
        return searchRouteAnnotationAttribute;
    }

    public Attribute getSearchShardAnnotationAttribute() {
        return searchShardAnnotationAttribute;
    }

    public static class AttPair extends Pair<Annotation, Attribute> {
        public AttPair(Annotation a, Attribute att){
            super(a, att);
        }

        @Override
        public Annotation getFirst() {
            return super.getFirst();    //To change body of overridden methods use File | Settings | File Templates.
        }

        @Override
        public void setFirst(Annotation first) {
            super.setFirst(first);    //To change body of overridden methods use File | Settings | File Templates.
        }

        @Override
        public Attribute getSecond() {
            return super.getSecond();    //To change body of overridden methods use File | Settings | File Templates.
        }

        @Override
        public void setSecond(Attribute second) {
            super.setSecond(second);    //To change body of overridden methods use File | Settings | File Templates.
        }
    }


}
