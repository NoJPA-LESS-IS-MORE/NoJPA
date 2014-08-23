package dk.lessismore.nojpa.solr;

import dk.lessismore.nojpa.db.methodquery.NList;
import dk.lessismore.nojpa.db.methodquery.NQL;
import dk.lessismore.nojpa.db.testmodel.Car;
import dk.lessismore.nojpa.db.testmodel.DatabaseCreatorTest;
import dk.lessismore.nojpa.db.testmodel.Person;
import dk.lessismore.nojpa.db.testmodel.PersonStatus;
import dk.lessismore.nojpa.reflection.db.DatabaseCreator;
import dk.lessismore.nojpa.reflection.db.model.ModelObjectSearchService;
import dk.lessismore.nojpa.reflection.db.model.ModelObjectService;
import dk.lessismore.nojpa.reflection.db.model.SolrServiceImpl;
import junit.framework.Assert;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.PivotField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.util.NamedList;
import org.junit.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by niakoi on 7/23/14.
 */
public class NQLTest {
    @Test
    public void test01() {
        DatabaseCreator.createDatabase("dk.lessismore.nojpa.db.testmodel");
        SolrServiceImpl solrService = new SolrServiceImpl();
        solrService.setCoreName("nojpa");
        solrService.setCleanOnStartup(true);

        ModelObjectSearchService.addSolrServer(Person.class, solrService.getServer());
        Person mPerson = NQL.mock(Person.class);

        for (int i = 0; i < 10; i++) {
            Person person = ModelObjectService.create(Person.class);
            person.setName("person " + (i % 4));
            person.setSomeFloat((float) (20f * Math.random()));
            if (i < 7) {
                Car car = ModelObjectService.create(Car.class);
                person.setCar(car);
            }
            ModelObjectService.save(person);
            ModelObjectSearchService.put(person);
        }
        solrService.commit();

        NList<Person> personsWithoutCar = NQL.search(mPerson).search(NQL.all(NQL.has(mPerson.getName(), NQL.Comp.EQUAL, "Person"), NQL.hasNull(mPerson.getCar()))).getList();
        Assert.assertEquals(personsWithoutCar.getNumberFound(), 3);

        NList<Person> personsWithoutCar2 = NQL.search(mPerson).searchIsNull(mPerson.getCar()).getList();
        Assert.assertEquals(personsWithoutCar2.getNumberFound(), 3);

        NList<Person> personsWithCar = NQL.search(mPerson).search(NQL.hasNotNull(mPerson.getCar())).getList();
        Assert.assertEquals(personsWithCar.getNumberFound(), 7);

        NList<Person> persons = NQL.search(mPerson).getList();
        Assert.assertEquals(persons.getNumberFound(), 10);

        {
            QueryResponse response = solrService.query(new SolrQuery("( (_Person_name__TXT:( Person )) AND -(_Person_car__ID:[\"\" TO *]) )"));
            Assert.assertEquals(response.getResults().getNumFound(), 3);
        }

        {

            String[] ss = new String[]{"Brian", "Das", "Mas", "Agne", "Carl", "Stefan", "Atanas", "Michael", "Camilla", "Sebastian"};
            for (int i = 0; i < 100; i++) {
                Person person = ModelObjectService.create(Person.class);
                person.setName(ss[(int) (10f * Math.random())]);
                person.setSomeFloat((float) (20f * Math.random()));
                if (i < 7) {
                    Car car = ModelObjectService.create(Car.class);
                    person.setCar(car);
                }
                ModelObjectService.save(person);
                ModelObjectSearchService.put(person);
            }
            solrService.commit();

            {
                SolrQuery query = new SolrQuery();
                query.setQuery("*:*");
                query.setFacet(true);
                query.addFacetQuery("_Person_someFloat__DOUBLE:[* TO 5]");
                query.addFacetQuery("_Person_someFloat__DOUBLE:[5 TO 15]");
                query.addFacetQuery("_Person_someFloat__DOUBLE:[15 TO *]");
                //query.addFacetField("_Person_name__TXT");
//                query.addFacetField("_Person_someFloat__DOUBLE");
                query.setFacetLimit(12);
//            query.set(FacetParams.FACET_QUERY, "_Person_name__TXT:person");
//            BoboRequestBuilder.applyFacetExpand(query, "color", true);

                QueryResponse response = solrService.query(query);

                System.out.println("response.getStatus() = " + response.getStatus());
                System.out.println("response.getResults().getNumFound() = " + response.getResults().getNumFound());
                System.out.println("response.getFacetQuery().size() = " + response.getFacetQuery().size());
                System.out.println("response.getFacetQuery().size() = " + response.getFacetFields().size());



                for(Iterator<String> iterator = response.getFacetQuery().keySet().iterator(); iterator.hasNext();  ){
                    String next = iterator.next();
                    System.out.println(next + " -> " + response.getFacetQuery().get(next));
                }


                for (int k = 0; k < response.getFacetFields().size(); k++) {
                    FacetField facetField = response.getFacetFields().get(k);
                    System.out.println("--------------------------- START");
                    System.out.println("facetField.getGap = " + facetField.getGap());
                    System.out.println("facetField.getName() = " + facetField.getName());
                    System.out.println("facetField.getValueCount() = " + facetField.getValueCount());
                    List<FacetField.Count> facetFieldValues = facetField.getValues();
                    for (int h = 0; h < facetFieldValues.size(); h++) {
                        FacetField.Count c = facetFieldValues.get(h);
                        System.out.println("c.getName() = " + c.getName());
                        System.out.println("c.getAsFilterQuery() = " + c.getAsFilterQuery());
                        System.out.println("c.getCount() = " + c.getCount());
                        System.out.println("c.getFacetField() = " + c.getFacetField());
                    }
                    System.out.println("facetField.getEnd() = " + facetField.getEnd());
                    System.out.println("--------------------------- END");
                }
            }

            {
                SolrQuery query = new SolrQuery();
                query.setQuery("*:*");
                query.setFacet(true);
                query.addFacetField("_Person_name__TXT");
                query.setFacetLimit(12);
//            query.set(FacetParams.FACET_QUERY, "_Person_name__TXT:person");
//            BoboRequestBuilder.applyFacetExpand(query, "color", true);

                QueryResponse response = solrService.query(query);

                System.out.println("response.getStatus() = " + response.getStatus());
                System.out.println("response.getResults().getNumFound() = " + response.getResults().getNumFound());
                System.out.println("response.getFacetQuery().size() = " + response.getFacetQuery().size());
                System.out.println("response.getFacetQuery().size() = " + response.getFacetFields().size());
                for (int k = 0; k < response.getFacetFields().size(); k++) {
                    FacetField facetField = response.getFacetFields().get(k);
                    System.out.println("--------------------------- START");
                    System.out.println("facetField.getGap = " + facetField.getGap());
                    System.out.println("facetField.getName() = " + facetField.getName());
                    System.out.println("facetField.getValueCount() = " + facetField.getValueCount());
                    List<FacetField.Count> facetFieldValues = facetField.getValues();
                    for (int h = 0; h < facetFieldValues.size(); h++) {
                        FacetField.Count c = facetFieldValues.get(h);
                        System.out.println("c.getName() = " + c.getName());
                        System.out.println("c.getAsFilterQuery() = " + c.getAsFilterQuery());
                        System.out.println("c.getCount() = " + c.getCount());
                        System.out.println("c.getFacetField() = " + c.getFacetField());
                    }
                    System.out.println("facetField.getEnd() = " + facetField.getEnd());
                    System.out.println("--------------------------- END");
                }
            }


        }

    }






    @Test
    public void test02() {
        DatabaseCreator.createDatabase("dk.lessismore.nojpa.db.testmodel");
        SolrServiceImpl solrService = new SolrServiceImpl();
        solrService.setCoreName("nojpa");
        solrService.setCleanOnStartup(true);

        ModelObjectSearchService.addSolrServer(Person.class, solrService.getServer());
        Person mPerson = NQL.mock(Person.class);

        for (int i = 0; i < 10; i++) {
            Person person = ModelObjectService.create(Person.class);
            person.setName("person " + (i % 4));
            person.setPersonStatus(PersonStatus.BETWEEN_RELATIONS);
            person.setSomeFloat((float) (20f * Math.random()));
            if (i < 7) {
                Car car = ModelObjectService.create(Car.class);
                person.setCar(car);
            }
            ModelObjectService.save(person);
            System.out.println("---------------- PUT START -----------------------");
            ModelObjectSearchService.put(person);
            System.out.println("---------------- PUT END -----------------------");
        }
        solrService.commit();

        NList<Person> personsWithoutCar = NQL.search(mPerson).search(NQL.all(NQL.has(mPerson.getPersonStatus(), NQL.Comp.EQUAL, PersonStatus.BETWEEN_RELATIONS), NQL.has(mPerson.getName(), NQL.Comp.EQUAL, "oasd_*+__asdads3cd& %%"), NQL.hasNull(mPerson.getCar()))).getList();
        //Assert.assertEquals(personsWithoutCar.getNumberFound(), 3);


    }
}