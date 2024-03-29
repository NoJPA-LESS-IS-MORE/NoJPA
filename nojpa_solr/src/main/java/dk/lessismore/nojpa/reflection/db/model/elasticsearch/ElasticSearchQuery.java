package dk.lessismore.nojpa.reflection.db.model.elasticsearch;

import dk.lessismore.nojpa.db.methodquery.NQL;
import dk.lessismore.nojpa.reflection.db.annotations.SearchIndex;
import dk.lessismore.nojpa.reflection.db.model.solr.SolrSearchQuery;
import org.apache.solr.client.solrj.SolrQuery;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class ElasticSearchQuery extends NQL.SearchQuery{


    private static final Logger log = LoggerFactory.getLogger(SolrSearchQuery.class);

    private SearchSourceBuilder queryBuilder;


    Calendar from = null;
    Calendar to = null;
    
    private String routing = null;
    private String[] indexs = null;

    public ElasticSearchQuery(Class selectClass) {
        super(selectClass);
        queryBuilder = new SearchSourceBuilder();
    }

//    @Override
//    public void addStats(Number variable) {
//        //TODO: Will make this in the future ...
//    }

    public String getRouting() {
        return routing;
    }

    private QueryBuilder buildSubQuery(NQL.NoSQLExpression expression){
        if(expression instanceof NQL.NoSQLNegatingExpression){
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            QueryBuilder qb = buildSubQuery(((NQL.NoSQLNegatingExpression) expression).getExpression());
            boolQueryBuilder.mustNot(qb);
            return boolQueryBuilder;
        } else if(expression instanceof NQL.NoSQLContainerExpression) {
            NQL.NoSQLContainerExpression root = (NQL.NoSQLContainerExpression) expression;
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            for(int i = 0; i < root.getExpressions().size(); i++) {
                NQL.NoSQLExpression exp = root.getExpressions().get(i);
                Integer condition = root.getConditions().get(i);
                QueryBuilder qb = buildSubQuery(exp);
                if(root.getExpressions().size() > 1){
                    if(NQL.NoSQLOperator.name(condition).equals("OR")){
                        boolQueryBuilder.should(qb);
                    } else {
                        boolQueryBuilder.must(qb);
                    }
                } else {
                    boolQueryBuilder.must(qb);
                }
            }

            return boolQueryBuilder;

        } else if(expression.getClass().equals(NQL.NoSQLExpression.class)){
            String attributeName = NQL.createFinalSolrAttributeName(expression.getJoints(), expression.getAttr());
//            String boostQuery = "";
//            String otherFunctions = " ";
//            for(int i = 0; i < expression.getNoSQLFunctions().size(); i++){
//                NQL.NoSQLFunction noSQLFunction = expression.getNoSQLFunctions().get(i);
//                if(noSQLFunction instanceof NQL.Boost){
//                    //                boostQuery = "^" + ((NQL.Boost)noSQLFunction).boost;
//                } else {
//                    otherFunctions += " " + noSQLFunction;
//                }
//            }
//            if (expression.getValue() == null) {
//                return otherFunctions;
//            }


            if(!expression.isNotNull() && !expression.isNull()){
                if(expression.getValue() instanceof Calendar){
                    
                    if(expression.getFrom() != null){
                        from = expression.getFrom();
                    }
                    if(expression.getTo() != null){
                        to = expression.getTo();
                    }
                    
                    
                    SimpleDateFormat xmlDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); //2011-11-28T18:30:30Z
                    xmlDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    //solrObj.addField(attributeName, xmlDateFormat.format(((Calendar) value).getTime()));
                    String statementValue = xmlDateFormat.format(((Calendar) expression.getValue()).getTime());
                    if(expression.getComparator() == NQL.Comp.EQUAL_OR_LESS){
//                        RangeQueryBuilder to = QueryBuilders.rangeQuery(attributeName).to((Calendar) expression.getValue());
                        Calendar end = Calendar.getInstance();
                        end.setTime(new Date(0));
                        RangeQueryBuilder to = QueryBuilders.rangeQuery(attributeName).lte(statementValue).gte(end);
                        return to;
                    } else if(expression.getComparator() == NQL.Comp.EQUAL_OR_GREATER){
//                        RangeQueryBuilder to = QueryBuilders.rangeQuery(attributeName).from((Calendar) expression.getValue());
                        Calendar end = Calendar.getInstance();
//                        end.setTime(new Date(Long.MAX_VALUE - 1));
                        RangeQueryBuilder to = QueryBuilders.rangeQuery(attributeName).gte(statementValue).lte(end);
                        return to;
                    } else if(expression.getComparator() == NQL.Comp.NOT_EQUAL){
                        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
                        MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder(attributeName, statementValue);
                        boolQueryBuilder.mustNot(matchQueryBuilder);
                        return boolQueryBuilder;
                    } else {
                        MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder(attributeName, statementValue);
                        return matchQueryBuilder;
                    }

                } else if(expression.getValueClazz().equals(Integer.class) || expression.getValueClazz().equals(Float.class) || expression.getValueClazz().equals(Long.class) || expression.getValueClazz().equals(Double.class) || expression.getValueClazz().equals(Number.class) ){
                    if(expression.getComparator() == NQL.Comp.EQUAL_OR_LESS){
                        RangeQueryBuilder to = QueryBuilders.rangeQuery(attributeName).to(expression.getValue()).from(Integer.MIN_VALUE);
                        return to;
                    } else if(expression.getComparator() == NQL.Comp.EQUAL_OR_GREATER){
                        RangeQueryBuilder to = QueryBuilders.rangeQuery(attributeName).from(expression.getValue()).to(Integer.MAX_VALUE);
                        return to;
                    } else if(expression.getComparator() == NQL.Comp.NOT_EQUAL){
                        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
                        MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder(attributeName,  expression.getValue());
                        boolQueryBuilder.mustNot(matchQueryBuilder);
                        return boolQueryBuilder;
                    } else {
                        MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder(attributeName,  expression.getValue());
                        return matchQueryBuilder;
                    }
                } else {
                    if(expression.isRouting() && expression.getValue() != null){
                        this.routing = "" + expression.getValue();
                    }
                    String statementValue = "" + NQL.removeFunnyChars("" + expression.getValue());
                    if (expression.getComparator() == NQL.Comp.EQUAL_OR_LESS) {
                        throw new RuntimeException("NQL.Comp.EQUAL_OR_LESS for Strings is not implemented");
                    } else if (expression.getComparator() == NQL.Comp.EQUAL_OR_GREATER) {
                        throw new RuntimeException("NQL.Comp.EQUAL_OR_GREATER for Strings is not implemented");
                    } else if (expression.getComparator() == NQL.Comp.NOT_EQUAL) {
                        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
                        MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder(attributeName, statementValue);
                        boolQueryBuilder.mustNot(matchQueryBuilder);
                        return boolQueryBuilder;
                    } else {
                        MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder(attributeName, statementValue);
                        return matchQueryBuilder;
                    }
                }


            } else if(expression.isNull()){
                throw new RuntimeException("expression.isNull() is not implemented");
            } else if(expression.isNotNull()){
                throw new RuntimeException("expression.isNotNull() is not implemented");
            }
        }
        String s = "Some error in the logic ... ";
        log.error(s);
        throw new RuntimeException(s);
    }


    protected void buildQueryRaw(){
        throw new RuntimeException("Unimplemented: buildQueryRaw");
    }

    protected void buildQuery(){
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
//        for(int i = 0; i < root.getExpressions().size(); i++) {
//            NQL.NoSQLExpression exp = root.getExpressions().get(i);
//            Integer condition = root.getConditions().get(i);
//            QueryBuilder qb = buildSubQuery(exp);
//            if(root.getExpressions().size() > 1){
//                if(NQL.NoSQLOperator.name(condition).equals("OR")){
//                    boolQueryBuilder.should(qb);
//                } else {
//                    boolQueryBuilder.must(qb);
//                }
//            } else {
//                boolQueryBuilder.must(qb);
//            }
//        }

        for (int i = 0; i < rootConstraints.size(); i++) {
            NQL.Constraint constraint = (NQL.Constraint) rootConstraints.get(i);
            NQL.NoSQLExpression expression = constraint.getExpression();
            QueryBuilder subQuery = buildSubQuery(expression);
            boolQueryBuilder.must(subQuery);
        }
        if(from != null) {
            SearchIndex searchIndexAnnotation = (SearchIndex) selectClass.getAnnotation(SearchIndex.class);
            if(searchIndexAnnotation != null) {
                indexs = calculateIndexs(from, to, searchIndexAnnotation.timeFrame());
                for(int i = 0; indexs != null && i < indexs.length; i++){
//                    log.debug("We will use index-suffix["+ (i) +"/"+ indexs.length +"]: " + indexs[i]);
                }
            }
        }


        if(rootConstraints.isEmpty()){
            queryBuilder.query(QueryBuilders.matchAllQuery());
        } else {
            queryBuilder.query(boolQueryBuilder);
        }
        log.debug("We will query elasticsearch = " + toStringDebugQuery());
        if (startLimit != -1) {
            queryBuilder.from(startLimit);
            queryBuilder.size(endLimit - startLimit);
        }
        if (this.orderByAttribute != null) {
            log.debug("Will sort by " + orderByAttribute + " with " + this.orderByORDER);
            queryBuilder.sort(this.orderByAttribute, this.orderByORDER == NQL.Order.ASC ? SortOrder.ASC : SortOrder.DESC);
        }
    }

    public static String[] calculateIndexs(Calendar from, Calendar to,  SearchIndex.TimeFrame searchIndexAnnotation) {
        if(from == null){
            return null;
        }
        if(to == null){
            to = Calendar.getInstance();
        }
        List<String> toReturn = new ArrayList<>(10);
        LocalDate fromDate = LocalDate.of(from.get(Calendar.YEAR), 1+from.get(Calendar.MONTH), from.get(Calendar.DAY_OF_MONTH));
        LocalDate toDate = LocalDate.of(to.get(Calendar.YEAR), 1+to.get(Calendar.MONTH), to.get(Calendar.DAY_OF_MONTH));
        long daysBetween = ChronoUnit.DAYS.between(fromDate, toDate);
        long monthsBetween = ChronoUnit.MONTHS.between(fromDate, toDate);




        if(daysBetween < 10){
//            System.out.println("toDate = " + toDate);
            for(int i = 0; fromDate.isBefore(toDate) || fromDate.equals(toDate); i++){
                toReturn.add("" + fromDate);
                from.add(Calendar.DAY_OF_YEAR, 1);
                fromDate = LocalDate.of(from.get(Calendar.YEAR), 1+from.get(Calendar.MONTH), from.get(Calendar.DAY_OF_MONTH));
            }
            return toReturn.toArray(new String[toReturn.size()]);
        } else if (monthsBetween < 10){
//            System.out.println("toDate = " + toDate);
            for(int i = 0;  fromDate.isBefore(toDate) || fromDate.equals(toDate); i++){
                toReturn.add(("" + fromDate).substring(0, 7) + "-*");
                from.add(Calendar.MONTH, 1);
                fromDate = LocalDate.of(from.get(Calendar.YEAR), 1+from.get(Calendar.MONTH), 1);
            }
            return toReturn.toArray(new String[toReturn.size()]);
        }
        return null;
    }

    @Override
    protected String toStringDebugQuery() {
        return queryBuilder.toString();
    }


    public String[] getIndexs() {
        return indexs;
    }

    public String getType() {
        return selectClass.getSimpleName();
    }

    public SearchSourceBuilder getQueryBuilder() {
        return queryBuilder;
    }
}
