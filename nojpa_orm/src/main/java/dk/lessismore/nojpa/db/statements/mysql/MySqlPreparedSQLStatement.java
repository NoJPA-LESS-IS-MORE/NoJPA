package dk.lessismore.nojpa.db.statements.mysql;

import dk.lessismore.nojpa.db.statements.LeafExpression;
import dk.lessismore.nojpa.db.statements.PreparedSQLStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

/**
 * Mysql statement implementation
 *
 * @author LESS-IS-MORE
 * @version 1.0 25-7-02
 */
public class MySqlPreparedSQLStatement implements PreparedSQLStatement {

    private static final Logger log = LoggerFactory.getLogger(MySqlPreparedSQLStatement.class);
    private List listOfLeafs = new LinkedList();

    public void addLeafExpression(LeafExpression expression) {
        //log.debug("addLeafExpression: " + ((MySqlLeafExpression) expression).getPreparedStatement());
        listOfLeafs.add(expression);
    }

    public void makeStatementReadyToExcute(PreparedStatement statement) throws SQLException {
        for (int i = 0; i < listOfLeafs.size(); i++) {
            //log.debug("adding " + (i + 1) + "leaf:" + listOfLeafs.get(i) +" = " + ((LeafExpression) listOfLeafs.get(i)).getPreparedValue());
            statement.setString(i + 1, ((LeafExpression) listOfLeafs.get(i)).getPreparedValue());
        }
    }
}
