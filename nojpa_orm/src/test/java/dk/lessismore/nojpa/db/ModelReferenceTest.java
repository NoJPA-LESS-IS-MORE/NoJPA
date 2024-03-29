package dk.lessismore.nojpa.db;

import dk.lessismore.nojpa.cache.ObjectCacheFactory;
import dk.lessismore.nojpa.db.methodquery.MQL;
import dk.lessismore.nojpa.db.testmodel.Car;
import dk.lessismore.nojpa.db.testmodel.Person;
import dk.lessismore.nojpa.db.testmodel.InitTestDatabase;
import dk.lessismore.nojpa.reflection.db.model.ModelObjectInterface;
import dk.lessismore.nojpa.reflection.db.model.ModelObjectService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.BeforeClass;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class ModelReferenceTest {

    @BeforeClass
    public static void initDatabase() {
        InitTestDatabase.initPlanetExpress();
//        InitTestDatabase.createTestData();
    }

    private static void save(ModelObjectInterface o){
        ModelObjectService.save(o);
    }



    @Test
    public void getEnumsArray() throws ParseException {
        {
            Person person = ModelObjectService.create(Person.class);
            person.setName("John");
            save(person);
            ObjectCacheFactory.getInstance().getObjectCache(Person.class).removeFromCache(person.getObjectID());
            MQL.selectByID(Person.class, person.getObjectID()).getPartners();
        }
        {
            Person person = ModelObjectService.create(Person.class);
            person.setName("John0");
            person.setPartners(Person.SEX.values());
            Assert.assertEquals(person.getPartners().length, 3);
            save(person);
            ObjectCacheFactory.getInstance().getObjectCache(Person.class).removeFromCache(person.getObjectID());
        }
        {
            Person person = ModelObjectService.create(Person.class);
            person.setName("John1");
            person.setPartners(Person.SEX.values());
            Assert.assertEquals(person.getPartners().length, 3);
            person.setPartners(null);
            Assert.assertNull(person.getPartners());
            person.setPartners(new Person.SEX[]{Person.SEX.MALE} );
            Assert.assertEquals(person.getPartners().length, 1);
            save(person);
            ObjectCacheFactory.getInstance().getObjectCache(Person.class).removeFromCache(person.getObjectID());
        }
        {
            Person person = ModelObjectService.create(Person.class);
            person.setName("John2");
            person.setPartners(Person.SEX.values());
            save(person);
            Assert.assertEquals(person.getPartners().length, 3);
            person.setPartners(null);
            save(person);
            Assert.assertNull(person.getPartners());
            person.setPartners(new Person.SEX[]{Person.SEX.MALE} );
            save(person);
            Assert.assertEquals(person.getPartners().length, 1);
            save(person);
            ObjectCacheFactory.getInstance().getObjectCache(Person.class).removeFromCache(person.getObjectID());
        }
        {
            Person person = ModelObjectService.create(Person.class);
            person.setName("John3");
            person.setPartners(Person.SEX.values());
            Assert.assertEquals(person.getPartners().length, 3);
            save(person);
            person.setPartners(null);
            Assert.assertNull(person.getPartners());
            save(person);
            person.setPartners(new Person.SEX[]{Person.SEX.MALE} );
            Assert.assertEquals(person.getPartners().length, 1);
            save(person);
            ObjectCacheFactory.getInstance().getObjectCache(Person.class).removeFromCache(person.getObjectID());
        }
        {
            List<Person> personList = MQL.select(Person.class).getList();
            for(Person p : personList){
                System.out.println( p + " -> " + Arrays.toString(p.getPartners()));
            }
        }
        {
            Person person = ModelObjectService.create(Person.class);
            person.setName("John");
            save(person);
            ObjectCacheFactory.getInstance().getObjectCache(Person.class).removeFromCache(person.getObjectID());
        }
        {
            List<Person> personList = MQL.select(Person.class).getList();
            for(Person p : personList){
                System.out.println( p + "("+ p.getName() +") -> " + Arrays.toString(p.getPartners()));
            }
        }
    }



    @Test
    public void getFunTestWithDirty() throws ParseException {
        Person person = ModelObjectService.create(Person.class);
        person.setName("John");

        SimpleDateFormat xmlDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //2011-11-28T18:30:30Z

        Calendar b1 = Calendar.getInstance();
        Date d = new Date();
        b1.setTime(xmlDateFormat.parse("2012-12-12 12:12:12"));
        Calendar b2 = Calendar.getInstance();
        b2.setTime(xmlDateFormat.parse("2012-12-12 12:12:12"));

        person.setBirthDate(b1);

        Car car = ModelObjectService.create(Car.class);
        car.setBrand("Ford Taunus");
        Car alfa = ModelObjectService.create(Car.class);
        alfa.setBrand("Alpha");
        save(alfa);

        Person female = ModelObjectService.create(Person.class);
        female.setName("Ina");
        female.setCar(alfa);
        System.out.println("Ina.isDirty:  ........ " + (female.isDirty() || female.isNew()));
        System.out.println("Alfa.isDirty:  ........ " + (alfa.isDirty() || alfa.isNew()));



        person.setCar(car);

        person.setSex( Person.SEX.MALE);

        save(person);


        person.setBirthDate(b2);


        car.setBrand("Ford Taunus2");
        person.setCar(car);
        person.setName("John");
        person.setSex( Person.SEX.MALE);


        System.out.println("Person.isDirty:  ........ " + (person.isDirty() || person.isNew()));
        System.out.println("Car.isDirty:  ........ " + (car.isDirty() || car.isNew()));
        System.out.println(" ........ " + b1.getTimeInMillis());
        System.out.println(" ........ " + b2.getTimeInMillis());


        save(person);

//        ObjectCacheFactory.getInstance().getObjectCache(Person.class).removeFromCache(person.getObjectID());

    }

    @Test
    public void getFunTestWithDirty2() throws ParseException {

        Car alfa = ModelObjectService.create(Car.class);
        alfa.setBrand("Alpha");
        save(alfa);

        Person female = ModelObjectService.create(Person.class);
        female.setName("Ina");
        female.setCar(alfa);
        save(female);

        Car f1 = female.getCar();
        Car f2 = female.getCar();
        Car f3 = female.getCar();
        Car f4 = female.getCar();
        System.out.println("...");

        System.out.println("f1.isDirty() = " + f1.isDirty());
        System.out.println("f2.isDirty() = " + f2.isDirty());
        System.out.println("f3.isDirty() = " + f3.isDirty());
        System.out.println("f4.isDirty() = " + f4.isDirty());
        f1.setBrand("asdadsads");
        System.out.println("f1.isDirty() = " + f1.isDirty());
        System.out.println("f2.isDirty() = " + f2.isDirty());
        System.out.println("f3.isDirty() = " + f3.isDirty());
        System.out.println("f4.isDirty() = " + f4.isDirty());

        female.setCar(alfa);




        System.out.println("Ina.isDirty:  ........ " + (female.isDirty() || female.isNew()));
        System.out.println("Alfa.isDirty:  ........ " + (alfa.isDirty() || alfa.isNew()));


        System.out.println("....");
        System.out.println("....");
        System.out.println("....");
        System.out.println("....");
        System.out.println("....");
        System.out.println("....");
        System.out.println("....");


        assertFalse(female.isDirty());



//        ObjectCacheFactory.getInstance().getObjectCache(Person.class).removeFromCache(person.getObjectID());

    }

    @Test
    public void getAfterSetTest() {
        Person person = ModelObjectService.create(Person.class);
        person.setName("John");

        assertNull(person.getCar());

        Car car = ModelObjectService.create(Car.class);
        car.setBrand("Ford Taunus");
        person.setCar(car);
        assertEquals(car, person.getCar());
        assertEquals(car.getBrand(), person.getCar().getBrand());

        car.setBrand("Toyota Carina");
        assertEquals(car, person.getCar());
        assertEquals(car.getBrand(), person.getCar().getBrand());

        save(person);
        //save(car);

        ObjectCacheFactory.getInstance().getObjectCache(Person.class).removeFromCache(person.getObjectID());

        Person person2 = MQL.selectByID(Person.class, person.getObjectID());
        person2.setCar(car);
        assertEquals(car, person2.getCar());
        assertEquals(car.getBrand(), person2.getCar().getBrand());

        car.setBrand("Citroen Berlingo");
        assertEquals(car, person2.getCar());
        assertEquals(car.getBrand(), person2.getCar().getBrand());
        save(car);
        assertEquals(car, person2.getCar());
        assertEquals(car.getBrand(), person2.getCar().getBrand());

    }



    @Test
    public void refObjectStillThere() throws InterruptedException {
        Thread.sleep(20);
        System.out.println("---------------------");
        Person person = ModelObjectService.create(Person.class);
        person.setName("John");

        Car car = ModelObjectService.create(Car.class);
        String carID = "" + car;
        car.setBrand("Ford Taunus");
        person.setCar(car);
        save(car);
        save(person);

        ObjectCacheFactory.getInstance().getObjectCache(Car.class).removeFromCache(carID);

        String newBrand = "Land Rover";
        Car nc = person.getCar();
        nc.setBrand(newBrand);


        assertEquals(person.getCar().getBrand(), MQL.selectByID(Car.class, carID).getBrand());
        save(person);

        ObjectCacheFactory.getInstance().getObjectCache(Car.class).removeFromCache(carID);
        Car car1 = MQL.selectByID(Car.class, carID);
        System.out.println(car1.getBrand());

        car1.setVolume(12121d);
        Thread.sleep(5 * 1000);
        save(car1);
        assertEquals(person.getCar().getBrand(), MQL.selectByID(Car.class, carID).getBrand());


    }


    @Test
    public void lastModified() throws InterruptedException {
        Thread.sleep(20);
        System.out.println("---------------------");
        Person person = ModelObjectService.create(Person.class);
        person.setName("John");
        String personID = "" + person;
        save(person);

        ObjectCacheFactory.getInstance().getObjectCache(Person.class).removeFromCache(personID);

        Thread.sleep(2 * 1000);

        Person pn = MQL.selectByID(Person.class, personID);
        pn.setName("asdasd");
        pn.setIsSick(true);
        save(pn);

        Thread.sleep(2 * 1000);

        person.setDescription("asdasdsad");


        //Should produce an Exception...
        save(person);


    }
}
