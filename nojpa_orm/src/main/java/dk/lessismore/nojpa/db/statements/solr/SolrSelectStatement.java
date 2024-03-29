//package dk.lessismore.reusable_v4.db.statements.solr;
//
//import dk.lessismore.reusable_v4.db.statements.PreparedSQLStatement;
//import dk.lessismore.reusable_v4.db.statements.SelectSQLStatement;
//import org.apache.solr.client.solrj.SolrQuery;
//
//import java.util.ArrayList;
//import java.util.Iterator;
//import java.util.LinkedList;
//import java.util.List;
//
///**
// * Created : with IntelliJ IDEA.
// * User: seb
// */
//public class SolrSelectStatement {
//
//    private final static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(SolrSelectStatement.class);
//
//    protected List attributeNames = null;
//    protected SolrQuery solrQuery = new SolrQuery();
//    protected int limitStart = -1;
//    protected int limitEnd = -1;
//
//    public SolrQuery getSolrQuery() {
//        return solrQuery;
//    }
//
//    protected List getAttributeNames() {
//        if (attributeNames == null) {
//            attributeNames = new LinkedList();
//        }
//        return attributeNames;
//    }
//
//    public String toString() {
//        return makeStatement();
//    }
//
//    public void addLimit(int start, int end) {
////        log.debug("START: addLimit(start("+ start +"), end("+ end +")) ... this.limitStart("+ this.limitStart +") this.limitEnd("+ this.limitEnd +")");
//        if(limitStart != -1 && start == 0 && end == 1){
//            this.limitEnd = this.limitStart + 1;
//        } else {
//            this.limitStart = start;
//            this.limitEnd = end;
//        }
////        log.debug("END: addLimit(start("+ start +"), end("+ end +")) ... this.limitStart("+ this.limitStart +") this.limitEnd("+ this.limitEnd +")");
//    }
//
//    public void removeAttributeName(String attributeName) {
//        getAttributeNames().remove(attributeName);
//    }
//
//    public void addAttributeName(String attributeName) {
//        getAttributeNames().add(attributeName);
//    }
//
//    public void addAttributeName(String tableName, String attributeName) {
//        getAttributeNames().add(tableName + "_" + attributeName);
//    }
//
//
//    public void setOrderBy(String attributeName, int sortType) {
//        solrQuery.addSort(attributeName, ((sortType == SelectSQLStatement.DESC) ? SolrQuery.ORDER.desc : SolrQuery.ORDER.asc));
//    }
//
//    public String makeStatement() {
////        log.debug("makeStatement() -START-START-START-START-START");
//
//        if (getTableNames().isEmpty()) {
//            throw new RuntimeException("Cant make statement without tablename");
//        }
//
//        StringBuilder statement = new StringBuilder();
//        statement.append("select");
//        if (!getAttributeNames().isEmpty()) {
//            Iterator iterator = getAttributeNames().iterator();
//            for (int i = 0; iterator.hasNext(); i++) {
//                if (i > 0) {
//                    statement.append(", ");
//                }
//                statement.append("\n\t").append(iterator.next());
//            }
//        } else {
//            statement.append("\n\t*");
//        }
//
//        statement.append("\nfrom");
//        Iterator iterator = getTableNames().iterator();
//        for (int i = 0; iterator.hasNext(); i++) {
//            if (i > 0) {
//                statement.append(", ");
//            }
//            statement.append("\n\t").append(iterator.next());
//        }
////        log.debug("makeStatement() super.makeStatement() - START");
//        statement.append("\n").append(super.makeStatement());
////        log.debug("makeStatement() super.makeStatement() - START");
//
//        for (int i = 0; !groupAttributeNameList.isEmpty() && i < groupAttributeNameList.size(); i++) {
//            if(i == 0){
//                statement.append("\nGROUP BY ");
//            } else {
//                statement.append(", ");
//            }
//            statement.append(groupAttributeNameList.get(i)).append(" ");
//        }
//        for (int i = 0; !sortAttributeNameList.isEmpty() && i < sortAttributeNameList.size(); i++) {
//            if(i == 0){
//                statement.append("\nORDER BY ");
//            } else {
//                statement.append(", ");
//            }
//            statement.append(sortAttributeNameList.get(i)).append(" ");
//        }
//        if (having != null) {
//            statement.append("\nHAVING ").append(having);
//        }
//        if(limitStart < 0 && limitEnd != -1) log.warn("limitStart cannot be negative except when both limits are -1",
//                new IllegalArgumentException("Negative limitStart (was " + limitStart + ")"));
//        if(limitEnd < 0 && limitStart != -1) log.warn("limitEnd cannot be negative except when both limits are -1",
//                new IllegalArgumentException("Negative limitEnd (was " + limitEnd + ")"));
//        if (limitStart != -1 && limitEnd != -1) {
//            statement.append("\nlimit " + limitStart + ", " + (limitEnd - limitStart));
//        }
//        return statement.toString();
//    }
//
//
//    public static void main(String[] args) {
//        /*MySqlSelectStatement s = new MySqlSelectStatement();
//        s.addTableName("skod");
//        s.addTableName("skod2");
//        s.addAttributeName("VoldsomVolvo");
//        s.addConstrain("VoldsomVolvo", WhereSQLStatement.EQUAL_AND_LESS, "cygnus year");
//        s.addJoin("VoldsomVolvo", "cygnus year");
//        System.out.println(s.makeStatement());*/
//    }
//
//
//
//}
