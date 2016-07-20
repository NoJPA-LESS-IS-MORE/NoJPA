package dk.lessismore.nojpa.db;

import dk.lessismore.nojpa.db.statements.*;
import dk.lessismore.nojpa.db.statements.mysql.*;
import dk.lessismore.nojpa.db.connectionpool.*;
import dk.lessismore.nojpa.resources.PropertyResources;
import dk.lessismore.nojpa.resources.PropertyService;
import dk.lessismore.nojpa.utils.EventCounter;

import java.sql.*;
import java.io.*;
import java.util.List;

/**
 * This class can execute an sql statement. It uses a connection pool of mysql connections.
 * If you want the sql statements can allso be redirected to an sql file, instead of
 * being executed.
 *
 * @author LESS-IS-MORE
 * @version 1.0 25-7-02
 */
public class SQLStatementExecutor {

    static {
        try{
            SQLStatementExecutor.doQuery("select 1+1;");
        } catch (Exception e){
            System.out.println("Some ERROR when warming up.... " + e);
            e.printStackTrace();
        }
    }



    private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(SQLStatementExecutor.class);
    private static boolean updateSqlToFile = false;
    private static boolean selectSqlToFile = false;
    private static boolean enableUpdateDbConnection = true;
    private static boolean enableSelectDbConnection = true;
    private static FileWriter sqlFileWriter = null;
    public static boolean debugMode = false;
    public static boolean debugCpuMode = false; //true  = memory leak
    private static EventCounter eventCounter = new EventCounter();

    static long totalCounter = 0;
    static long totalTime = 0;



    static {
        PropertyResources websiteResources = PropertyService.getInstance().getPropertyResources(SQLStatementExecutor.class);
        if (websiteResources.getString("debug") != null && websiteResources.getBoolean("debug")) {
            debugMode = true;
        } else {
            debugMode = false;
        }
        if (websiteResources.getString("updateSqlToFile") != null && websiteResources.getBoolean("updateSqlToFile")) {
            updateSqlToFile = true;
        } else {
            updateSqlToFile = false;
        }
        if (websiteResources.getString("sqlFileName") != null) {
            setSqlFileName(websiteResources.getString("sqlFileName"));
        }
    }



    public static EventCounter getEventCounter(){ return eventCounter; }

    private static long lastConnectionUsed = 0;

    public static void setUpdateSqlToFile(boolean updateSqlToFile) {
        SQLStatementExecutor.updateSqlToFile = updateSqlToFile;
    }

    public static void setSelectSqlToFile(boolean selectSqlToFile) {
        SQLStatementExecutor.selectSqlToFile = selectSqlToFile;
    }

    public static void setEnableUpdateDbConnection(boolean enableUpdateDbConnection) {
        SQLStatementExecutor.enableUpdateDbConnection = enableUpdateDbConnection;
    }

    public static void setEnableSelectDbConnection(boolean enableSelectDbConnection) {
        SQLStatementExecutor.enableSelectDbConnection = enableSelectDbConnection;
    }

    public static void setSqlFileName(String sqlFileName) {
        try {
            if (sqlFileWriter != null) {
                try {
                    sqlFileWriter.close();
                    sqlFileWriter = null;
                } catch (Exception e) {
                    log.error("Could not close old sql output file: " + sqlFileName, e);
                }
            }
            sqlFileWriter = new FileWriter(sqlFileName);
        } catch (Exception e) {
            log.error("Could not create sql output file: " + sqlFileName, e);
            sqlFileWriter = null;
        }
    }


    public static void addSqlStatementToSqlFile(String sqlStatement) {
        try {
            if (sqlFileWriter != null) {
                sqlFileWriter.write(sqlStatement + ";\n");
                sqlFileWriter.flush();
            }
        } catch (Exception e) {
            log.error("Failed sql write", e);
        }
    }

    public static boolean doUpdate(String sqlStatement) {
        if (updateSqlToFile) {
            addSqlStatementToSqlFile(sqlStatement.replaceAll("\n", " "));
        }
        if (enableUpdateDbConnection) {
            Connection connection = null;
            Statement statement = null;
            try {
                connection = (Connection) ConnectionPoolFactory.getPool().getFromPool();
                log.debug("Will update with:::" + sqlStatement.replaceAll("\n", " "));
                long start = System.currentTimeMillis();
                statement = connection.createStatement();
                statement.execute(sqlStatement);
                long end = System.currentTimeMillis();
                long time = end - start;
                totalTime = totalTime + time;
                totalCounter++;
//                log.debug("**************** AVG-TIME("+ (totalTime / totalCounter) +") count("+ totalCounter +") totalTime("+ totalTime +") lastTime("+ time +")");


                if(debugCpuMode) {
                    eventCounter.newEvent(sqlStatement.replaceAll("\n", " "), end - start);
                    eventCounter.newEvent("insert", end - start);
                }
                statement.close();
                ConnectionPoolFactory.getPool().putBackInPool(connection);
                return true;

            } catch (Exception e) {
                log.error("Update/Insert sql execution failed \nstmt=" + sqlStatement, e);
                try {
                    if (statement != null) {
                        statement.close();
                    }
                    statement = null;
                    if (connection != null) {
                        connection.close();
                    }
                    ConnectionPoolFactory.getPool().addNew();
                } catch (Exception ex) {
                    log.warn("Trying to close connection because of error ..." + ex.toString());
                }
                connection = (Connection) ConnectionPoolFactory.getPool().getFromPool();
                try {
                    statement = connection.createStatement();
                    statement.execute(sqlStatement);
                    ConnectionPoolFactory.getPool().putBackInPool(connection);
                    return true;
                } catch (Exception ex) {
                    try {
                        if (statement != null) {
                            statement.close();
                        }
                        statement = null;
                        if (connection != null) {
                            connection.close();
                        }
                        ConnectionPoolFactory.getPool().addNew();
                    } catch (Exception exp) {
                        log.warn("Trying to close connection because of error ..." + exp.toString());
                    }
                    return false;
                }
            } finally {
                try {
                    if (statement != null) {
                        statement.close();
                    }
                    statement = null;
                } catch (Exception ex) {
                    //Nothing
                }
            }
        } else {
            return true;
        }
    }

    /* is used for alter index's - which properly will give a exception, because index already exists */
    public static boolean doUpdateAndIgnoreExceptions(String sqlStatement) {
        if (enableUpdateDbConnection) {
            Connection connection = null;
            Statement statement = null;
            try {
                connection = (Connection) ConnectionPoolFactory.getPool().getFromPool();
                long start = System.currentTimeMillis();
                statement = connection.createStatement();
                log.debug("Will update with:" + sqlStatement.replaceAll("\n", " "));
                statement.execute(sqlStatement);

                long end = System.currentTimeMillis();
                long time = end - start;
                totalTime = totalTime + time;
                totalCounter++;
//                log.debug("**************** AVG-TIME("+ (totalTime / totalCounter) +") count("+ totalCounter +") totalTime("+ totalTime +") lastTime("+ time +")");


                if(debugCpuMode) {
                    eventCounter.newEvent(sqlStatement.replaceAll("\n", " "), end - start);
                    eventCounter.newEvent("insert", end - start);
                }
                statement.close();
                ConnectionPoolFactory.getPool().putBackInPool(connection);
                return true;

            } catch (Exception e) {
                return true;
            } finally {
                try {
                    if (statement != null) {
                        statement.close();
                    }
                    statement = null;
                } catch (Exception ex) {
                    //Nothing
                }
            }
        } else {
            return true;
        }
    }

    public static LimResultSet doQuery(String sqlStatement) {
        if (selectSqlToFile) {
            addSqlStatementToSqlFile(sqlStatement.replaceAll("\n", " "));
        }
        if (enableSelectDbConnection) {
            Statement statement = null;
            Connection connection = null;
            ResultSet resultSet = null;
            try {
                //if(debugMode){
                    //eventCounter.newEvent(sqlStatement);
                    log.debug("doQuery: Will run: " + sqlStatement.replaceAll("\n", " "));
//                    log.debug("DEBUG-TRACE", new Exception("DEBUG"));
                //}
                connection = (Connection) ConnectionPoolFactory.getPool().getFromPool();
                long start = System.currentTimeMillis();
                statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                resultSet = statement.executeQuery(sqlStatement);
                long end = System.currentTimeMillis();
                long time = end - start;
                totalTime = totalTime + time;
                totalCounter++;
//                log.debug("**************** AVG-TIME("+ (totalTime / totalCounter) +") count("+ totalCounter +") totalTime("+ totalTime +") lastTime("+ time +")");

                if(debugCpuMode) eventCounter.newEvent(sqlStatement.replaceAll("\n", " "), end - start);
                LimResultSet toReturn = new LimResultSet(resultSet, statement, sqlStatement);
                ConnectionPoolFactory.getPool().putBackInPool(connection);
//                log.debug("ZZZZZZZZZZ toReturn("+ toReturn +"), resultSet("+ resultSet +"), toReturn("+ toReturn +"), toReturn.getResultSet("+ ( toReturn != null ? toReturn.getResultSet() : null ) +")");
                return toReturn;
            } catch (Exception e) {
                log.error("query sql execution failed \nstmt=" + sqlStatement, e);
                try {
                    if (resultSet != null) {
                        resultSet.close();
                    }
                    resultSet = null;
                    if (statement != null) {
                        statement.close();
                    }
                    statement = null;
                    if (connection != null) {
                        connection.close();
                    }
                    ConnectionPoolFactory.getPool().addNew();
                } catch (Exception ex) {
                    log.error("2:Trying to close connection because of error ..." + ex.toString());
                }
                try {
                    connection = (Connection) ConnectionPoolFactory.getPool().getFromPool();
                    statement = connection.createStatement();
                    resultSet = statement.executeQuery(sqlStatement);
                    LimResultSet toReturn = new LimResultSet(resultSet, statement, sqlStatement);
                    ConnectionPoolFactory.getPool().putBackInPool(connection);
                    return toReturn;
                } catch (Exception ex) {
                    log.error("Some error in doQuery " + e.toString());
                    try {
                        if (resultSet != null) {
                            resultSet.close();
                        }
                        resultSet = null;
                        if (statement != null) {
                            statement.close();
                        }
                        statement = null;
                        if (connection != null) {
                            connection.close();
                        }
                        ConnectionPoolFactory.getPool().addNew();
                    } catch (Exception exp) {
                        log.warn("2:Trying to close connection because of error ..." + exp.toString());
                    }
                }
            }
        }
        return null;
    }


    public static LimResultSet doQuery(SelectSQLStatement selectSQLStatement) {
        if(debugMode){
            return doQuery("" + selectSQLStatement);
        } else {
            PreparedStatement statement = null;
            Connection connection = null;
            ResultSet resultSet = null;
            try {
                PreparedSQLStatement preSQLStatement = new MySqlPreparedSQLStatement();
                String initStatement = selectSQLStatement.makePreparedStatement(preSQLStatement);
                if (selectSqlToFile) {
                    addSqlStatementToSqlFile(initStatement.replaceAll("\n", " "));
                }
                //if(debugMode){
                //    eventCounter.newEvent(initStatement);
                //}
                //log.debug("doQuery: Will run preparedStatement: " + initStatement);
                connection = (Connection) ConnectionPoolFactory.getPool().getFromPool();

                long start = System.currentTimeMillis();
                statement = connection.prepareStatement(initStatement, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                log.debug("doQuery: Will run: " + initStatement.replaceAll("\n", " "));
                preSQLStatement.makeStatementReadyToExcute(statement);
                resultSet = statement.executeQuery();
                long end = System.currentTimeMillis();
                long time = end - start;
                totalTime = totalTime + time;
                totalCounter++;
//                log.debug("**************** AVG-TIME("+ (totalTime / totalCounter) +") count("+ totalCounter +") totalTime("+ totalTime +") lastTime("+ time +")");

                if(debugCpuMode) eventCounter.newEvent(initStatement.replaceAll("\n", " "), end - start);
                log.debug("Time("+ (end - start) +") for " + initStatement.replaceAll("\n", " "));
                LimResultSet toReturn = new LimResultSet(resultSet, statement, initStatement);
                ConnectionPoolFactory.getPool().putBackInPool(connection);
                return toReturn;
            } catch (Exception e) {
                log.error("doQuery: query sql execution failed", e);
                try {
                    if (statement != null) {
                        statement.close();
                    }
                    if (connection != null) {
                        connection.close();
                    }
                    ConnectionPoolFactory.getPool().addNew();
                } catch (Exception ex) {
                    log.warn("2:Trying to close connection because of error ..." + ex.toString());
                }
            }
            return null;
        }
    }

    static long start = 0L;

    public static void print(String str) {
        log.debug(str + " " + (System.currentTimeMillis() - start));
    }

    public static void printOutCpuStats(){
        List<EventCounter.Event> status = SQLStatementExecutor.getEventCounter().getStatus();
        for(int i = 0; i < status.size() && i < 200; i++){
            log.debug(status.get(i).countOfEvents + " \t " + status.get(i).totalTime + " \t " + status.get(i).key);
        } 
    }


    public static void main(String[] args) throws Exception {
        start = System.currentTimeMillis();
        for(int j = 0; j < 10; j++){
            long microStart = System.currentTimeMillis();
            print("START " + j);
            LimResultSet s = doQuery("select * from _Order where creationDate > '2006-04-01'");
            ResultSet resultSet = s.getResultSet();
            for(int i = 0; resultSet.next(); i++) {
              resultSet.getString("number");
            }
            print("END " + (System.currentTimeMillis() - microStart)+" " +  j);
        }
        print("ENDS ");


    }


}
