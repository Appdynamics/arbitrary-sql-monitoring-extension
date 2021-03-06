package com.singularity.ee.agent.systemagent.monitors;

import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class ArbitrarySqlMonitor extends AManagedMonitor
{
    private static final Log logger = LogFactory.getLog(ArbitrarySqlMonitor.class);

    private boolean cleanFieldNames;
    private String driverClass;
    private String metricPath;
    private String username;
    private String password;
    private String url;
    private String database;
    private String sql;
    
    public static String METRIC_CONNECTION_TIME_IN_MS = "Connection Time (ms)";
    public static String METRIC_ROWS_RETURNED         = "Rows Returned";
    public static String METRIC_EXECUTION_TIME_IN_MS  = "Execution Time (ms)";


    private String getArg(Map<String, String> map, String key, String defaultValue)
    {
        return map.containsKey(key) ? map.get(key) : defaultValue;
    }

    private String getArg(Map<String, String> map, String key)
    {
        if (!map.containsKey(key))
        {
            throw new IllegalArgumentException(key);
        }
        return map.get(key);
    }

    protected void printMetric(String name, String value, String aggType, String timeRollup, String clusterRollup)
    {
        String metricName = getMetricPrefix() + name;
        MetricWriter metricWriter = getMetricWriter(metricName, aggType, timeRollup, clusterRollup);
        metricWriter.printMetric(value);

        // just for debug output
        if (logger.isDebugEnabled())
        {
            logger.debug("METRIC:  NAME:" + metricName + " VALUE:" + value + " :" + aggType + ":" + timeRollup + ":"
                    + clusterRollup);
        }
    }
    
	private void printMetric(String metricPath, String metricValue) {
		printMetric(metricPath, metricValue, MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
	}

    private String cleanFieldName(String name)
    {
        if (cleanFieldNames)
        {
            /**
             * Unicode characters sometimes look weird in the UI, so we replace all Unicode hyphens with
             * regular hyphens. The \d{Pd} character class matches all hyphen characters.
             * @see <a href="URL#http://www.regular-expressions.info/unicode.html">this reference</a>
             */
            return name.replaceAll("\\p{Pd}", "-")
                    .replaceAll("_", " ");
        }
        else
        {
            return name;
        }
    }

    public TaskOutput execute(Map<String, String> taskParams, TaskExecutionContext taskContext)
            throws TaskExecutionException
    {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try
        {
            logger.info("Starting arbitrary SQL execution monitor");

            cleanFieldNames = Boolean.parseBoolean(getArg(taskParams, "clean-field-names", "true"));
            metricPath = getArg(taskParams, "metric-path", null);
            driverClass = getArg(taskParams, "driver-class", null);
            username = getArg(taskParams, "username", null);
            password = getArg(taskParams, "password", null);
            database = getArg(taskParams, "database", null);
            url = getArg(taskParams, "url");
            sql = getArg(taskParams, "sql");

            if (StringUtils.isNotEmpty(driverClass))
            {
                logger.info("Loading JDBC driver class " + driverClass);
                Class.forName(driverClass);
            }

            logger.info("Opening connection to " + url);
            long startTime = System.currentTimeMillis();
            conn = DriverManager.getConnection(url, username, password);
            if (StringUtils.isNotEmpty(database))
            {
                logger.info("Changing catalog to " + database);
                conn.setCatalog(database);
            }
            long elapsedTime = System.currentTimeMillis() - startTime;
            printMetric(METRIC_CONNECTION_TIME_IN_MS, Long.toString(elapsedTime));
            logger.debug("Connection time = " + elapsedTime);

            logger.info("Executing query: " + sql);
            startTime = System.currentTimeMillis();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);

            logger.info("Reading results");
            long rowCount = 0;
            while (rs.next())
            {
                String key = cleanFieldName(rs.getString(1));

                for (int i = 2; i <= rs.getMetaData()
                        .getColumnCount(); i++)
                {
                    String metricName = cleanFieldName(rs.getMetaData()
                            .getColumnName(i));
                    printMetric(key + "|" + metricName,  rs.getString(i));
                    logger.debug(key + "|" + metricName + " = " + rs.getString(i));
                }

                rowCount += 1;
            }

            elapsedTime = System.currentTimeMillis() - startTime;
            printMetric(METRIC_EXECUTION_TIME_IN_MS, Long.toString(elapsedTime));
            logger.debug("Execution time = " + elapsedTime);
            printMetric(METRIC_ROWS_RETURNED, Long.toString(rowCount));
            logger.debug("Rows returned = " + rowCount);

            logger.info("Query completed successfully");
        }
        catch (ClassNotFoundException e)
        {
            logger.error("Unable to load JDBC driver " + driverClass, e);
            return null;
        }
        catch (SQLException e)
        {
            logger.error("SQL exception", e);
            return null;
        }
        catch (IllegalArgumentException e)
        {
            logger.fatal("Required parameter not specified in monitor.xml: " + e.getMessage());
            return null;
        }
        finally
        {
            logger.info("Closing connection");

            try
            {
                if (rs != null)
                    rs.close();
                if (stmt != null)
                    stmt.close();
                if (conn != null)
                    conn.close();
            }
            catch (Exception e)
            {
                logger.error("Error cleaning up: " + e.getMessage(), e);
            }
        }

        return new TaskOutput("Success");
    }

    protected String getMetricPrefix()
    {
        if (metricPath != null)
        {
            if (!metricPath.endsWith("|"))
            {
                metricPath += "|";
            }
            return metricPath;
        }
        else
        {
            return "Custom Metrics|SQLMonitor|";
        }
    }


    public static void main(String[] argv)
            throws Exception
    {
        Map<String, String> taskParams = new HashMap<String, String>();
        taskParams.put("driver-class", "com.mysql.jdbc.Driver");
        taskParams.put("url", "jdbc:mysql://localhost:3388");
        taskParams.put("database", "controller");
        taskParams.put("username", "root");
        taskParams.put("password", "admin1-2");
        taskParams.put("metric-path", "Server|Component:ArbitrarySqlMonitor|SolarWinds");
        taskParams.put("sql",
                "SELECT a.name AS \"Name\", count(DISTINCT mm.node_id) AS \"Node Count\", count(*) AS \"Metric Count\" FROM metricdata_min mm JOIN application a ON (a.id = mm.application_id) WHERE mm.ts_min = (SELECT max(ts_min) - 1 FROM metricdata_min) GROUP BY 1 ORDER BY 1");
        new ArbitrarySqlMonitor().execute(taskParams, null);
    }
}
