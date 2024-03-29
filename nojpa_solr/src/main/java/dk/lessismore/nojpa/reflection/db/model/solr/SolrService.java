package dk.lessismore.nojpa.reflection.db.model.solr;

import dk.lessismore.nojpa.reflection.db.model.nosql.NoSQLService;
import dk.lessismore.nojpa.reflection.translate.TranslateService;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;

import java.io.IOException;
import java.util.Locale;

/**
 * Created by seb on 7/23/14.
 */
public interface SolrService extends NoSQLService {

    QueryResponse query(SolrQuery query, String postShardName);

}
