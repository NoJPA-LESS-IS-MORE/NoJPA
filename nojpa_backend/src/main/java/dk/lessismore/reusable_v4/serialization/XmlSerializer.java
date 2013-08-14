package dk.lessismore.reusable_v4.serialization;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.mapper.MapperWrapper;

import java.io.*;

public class XmlSerializer implements Serializer {

    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(XmlSerializer.class);

    protected final XStream xstream = new XStream() {
            protected MapperWrapper wrapMapper(MapperWrapper next) {
                return new ModelMapper(next);
            }
        };

    public XmlSerializer() {
        //XStream xstream = new XStream(new DomDriver());
        xstream.registerConverter(new ModelConverter(xstream.getMapper()));
    }

    public String serialize(Object object) {
        return xstream.toXML(object);
    }
//    public String serialize(Object object) {
//        try{
//            return MiniXmlUtil.toXml(object);
//        } catch(Exception e){
//            e.printStackTrace();
//            log.error("serialize have problems : " + e, e);
//            throw new RuntimeException("serialize have problems : " + e, e);
//
//        }
//    }

    public void store(Object object, File file) throws IOException {
        String serializedObject = serialize(object);
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(serializedObject);
        writer.flush();
        writer.close();
    }

    public <T> T unserialize(String serializedString) {
        if(serializedString == null){ return null; }
        log.debug("START: Will now unserialize:\n------------------------");
        log.debug(serializedString != null && serializedString.length() > 300 ? serializedString.substring(0, 300) + "...zz...": serializedString);
        log.debug("END: ------------------------");
        return (T) xstream.fromXML(serializedString);
//        try {
//            return (T) MiniXmlUtil.fromXml(serializedString);
//        } catch (Exception e) {
//            e.printStackTrace();
//            log.error("unserialize have problems : " + e, e);
//            throw new RuntimeException("serialize have problems : " + e, e);
//        }

    }

    public Object restore(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            content.append(line);
            content.append("\n");
        }
        reader.close();
        return unserialize(content.toString());
    }
}
