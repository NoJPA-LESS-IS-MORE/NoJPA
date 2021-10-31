package dk.lessismore.nojpa.db.methodquery;

import dk.lessismore.nojpa.reflection.db.model.ModelObjectInterface;
import dk.lessismore.nojpa.utils.Pair;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.Calendar;
import java.util.List;

/**
 * Created : with IntelliJ IDEA.
 * User: seb
 */
public interface NList<C extends ModelObjectInterface> extends List<C> {
    long getNumberFound();
    long getQTime();
    long getQTimeIncludingNetwork();


    <N extends Number> NStats<N> getStats(N variable);
    List<Pair<String, Long>> getFacet(Object variable);
    Object getDateRangeFacet(Object variable);
    Float getScore(int index);
    String getPostShardName();
    Aggregations getAggregations();

    Object getImpl();


}
