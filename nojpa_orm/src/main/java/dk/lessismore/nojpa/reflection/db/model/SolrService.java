package dk.lessismore.nojpa.reflection.db.model;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;

/**
 * Created by niakoi on 7/23/14.
 */
public interface SolrService {
    SolrServer getServer();

    public void startup();

    void index(SolrInputDocument solrInputDocument);
    QueryResponse query(SolrQuery query);
    NamedList<Object> request(SolrRequest req);
    <T> T getByID(String unTransTextSHA, Class<T> type);

    void commit();
    void optimize();
    void delete(String id);
    void empty();
    void destroy();
    void deleteAll();
}
