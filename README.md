Title:  Arbitrary SQL Monitor for AppDynamics
Author: Todd Radel <tradel@appdynamics.com>
Date:   7 April 2014


# Arbitrary SQL Monitor for AppDynamics #

An AppDynamics Machine Agent extension to run a SQL statement against a JDBC database, and import the results of the query as custom metrics in AppDynamics.

Sample use cases:

 * Import business metrics and KPI's from an application database.
 * Import performance data from SolarWinds or other system monitors that use an RDBMS backend.

This extension requires the Java Machine Agent.


## Installation ##

1. Download ArbitrarySQLMonitor.zip from the Community site.
2. Copy ArbitrarySQLMonitor.zip into the directory where you installed the machine agent, under `$AGENT_HOME/monitors`.
3. Unzip the file. This will create a new directory called ArbitrarySQLMonitor.
4. In `$AGENT_HOME/monitors/ArbitrarySQLMonitor`, edit the file monitor.xml and configure the plugin.
5. Copy your JDBC driver jarfile into `$AGENT_HOME/monitors/ArbitrarySQLMonitor/lib`.
5. Restart the machine agent.


## Configuration ##

Configuration for this monitor is in the `monitor.xml` file in the monitor directory. All of the configurable options are in the `<task-arguments>` section.

driver-class
: Class name of the JDBC driver to use. 

url
: JDBC URL to the database.

database
: (Optional) Name of the database to select after connecting.

username
: Database user ID.

password
: Password.

sql
: The query to be executed.

metric-path
: (Optional) Path in the metric tree where the metrics should be placed. If not
  specified, the metrics will be placed under "Custom Metrics|SQLMonitor".
        

## JDBC Driver Reference ##

To use this extension, you will need to provide the JDBC driver, class name, and connection URL. We've provided examples for some of the common databases. You'll need to replace the placeholders (HOST, PORT, DB, etc.) in the URL with your own values. 

**NOTE:** This information was gathered from various sources on the web. AppDynamics takes no responsibility for its current accuracy. When in doubt, consult the documentation for your database.

| Database | Class Name | Driver Jar | Sample JDBC URL | Download Link |
| :-- | :-- | :-- | :-- | :-- |
| Firebird SQL | org.firebirdsql.jdbc.FBDriver | firebirdsql-full.jar | `jdbc:firebirdsql://HOST:PORT/DB` | <http://www.firebirdsql.org/> |
| Oracle | oracle.jdbc.OracleDriver | ojdbc6.jar | `jdbc:oracle:oci:@SID` | <http://www.oracle.com/technetwork/database/features/jdbc/index-091264.html> |
| H2 Database Engine | org.h2.Driver | h2.jar | `jdbc:h2:file:FILENAME` | <http://www.h2database.com> |
| HSQLDB | org.hsqldb.jdbcDriver | hsqldb.jar | `jdbc:hsqldb:file:FILENAME`, `jdbc:hsqldb:hsql://HOST:PORT/DB` | <http://hsqldb.sourceforge.net> |
| IBM DB2 | com.ibm.db2.jcc.DB2Driver	 | db2jcc4.jar | `jdbc:db2://HOST:PORT/DB` | <http://www-01.ibm.com/software/data/db2/linux-unix-windows/download.html> |
| IBM DB2 for iSeries | com.ibm.as400.access.AS400JDBCDriver | jt400.jar | `jdbc:as400://HOST` | <http://www-01.ibm.com/software/data/db2/java/> |
| Apache Derby | org.apache.derby.jdbc.EmbeddedDriver | derby.jar | `jdbc:derby:DB` | <http://db.apache.org/derby/> |
| Teradata | com.teradata.jdbc.TeraDriver	 | terajdbc4.jar | `jdbc:teradata://DB` | <http://www.teradata.com/DownloadCenter/Forum158-1.aspx> |
| Sybase SQL Anywhere | com.sybase.jdbc3.jdbc.SybDriver | jconnect.jar | `jdbc:sybase:Tds:HOST:PORT` | <http://www.sybase.com/products/allproductsa-z/softwaredeveloperkit/jconnect> |
| MySQL | com.mysql.jdbc.Driver | mysql-connector-java-VERSION-bin.jar | `jdbc:mysql://HOST:PORT/DB` | <http://www.mysql.com/downloads/connector/j/> |
| SQL Server (Microsoft driver) | com.microsoft.sqlserver.jdbc.SQLServerDriver | sqljdbc4.jar | `jdbc:sqlserver://HOST;instanceName=INSTANCE;DatabaseName=DB;` | <http://msdn.microsoft.com/en-gb/data/aa937724%28en-us%29.aspx> |
| SQL Server (jTDS driver) | net.sourceforge.jtds.jdbc.Driver | jtds.jar | `jdbc:jtds:sqlserver://HOST:PORT/DB` | <http://jtds.sourceforge.net> |
| PostgreSQL | org.postgresql.Driver | postgresql-VERSION.jdbc4.jar | `jdbc:postgresql://HOST:PORT/DB` | <http://jdbc.postgresql.org/download.html> |
| Informix | com.informix.jdbc.IfxDriver | ifxjdbc.jar | `jdbc:informix-sqli://HOST:PORT/DB:informixserver=INSTANCE` | <http://www14.software.ibm.com/webapp/download/search.jsp?go=y&rs=ifxjdbc> |
| ODBC Bridge | sun.jdbc.odbc.JdbcOdbcDriver | Included in the JDK | `jdbc:odbc:DB` | None needed |


## Metrics Provided ##

The metrics created by this extension depend on the query you provide. The column names will be used as the metric names, and the first column of each row will be used as a folder name.

For example, the query `SELECT "A", 3 as "B"` would create a new metric folder called `A`, with a new metric `B` whose value would be 3.

The extension also creates three metrics with information about the query itself:

Connection Time (ms)
: Time required to open a database connection and change catalogs, in milliseconds.

Execution Time (ms)
: Time required to execute the query and fetch all result rows, in milliseconds.

Rows Returned
: Total number of rows returned by the query.

See "Example Usage" below for a concrete example.

### Restrictions ###

 * The first column is assumed to be a string. All other columns are assumed to be long integers.
 * Multiple result sets are not supported. 


## Example Usage ##

Here's a sample query that runs against an AppDynamics controller and reports some data about the applications registered with the controller.

``` sql
    SELECT a.name AS "Name",
           count(DISTINCT mm.node_id) AS "Node Count",
           count(*) AS "Metric Count"
    FROM metricdata_min mm
    JOIN application a ON (a.id = mm.application_id)
    WHERE mm.ts_min =
        (SELECT max(ts_min) - 1
         FROM metricdata_min)
    GROUP BY 1
    ORDER BY 1
```
 
That query will return results something like this:

| Name            | Node Count | Metric Count |
|-----------------|-----------:|-------------:|
| ACME Book Store | 3          | 42591        |
| Bundy Shoes     | 7          | 72390        |
| Big Deal Retail | 6          | 65440        |

You would configure the extension like this in your `monitor.xml`:

``` xml
        <task-arguments>
            <argument name="driver-class" is-required="false" default-value="com.mysql.jdbc.Driver" />
            <argument name="url" is-required="true" default-value="jdbc:mysql://CONTROLLER:3306/controller" />
            <argument name="username" is-required="false" default-value="root"/>
            <argument name="password" is-required="false" default-value="PASSWORD"/>
            <argument name="sql" is-required="false" default-value="SELECT a.name AS &quot;Name&quot;, count(DISTINCT mm.node_id) AS &quot;Node Count&quot;, count(*) AS &quot;Metric Count&quot; FROM metricdata_min mm JOIN application a ON (a.id = mm.application_id) WHERE mm.ts_min = (SELECT max(ts_min) - 1 FROM metricdata_min) GROUP BY 1 ORDER BY 1"/>
        </task-arguments>
        <java-task>
            <classpath>SqlMonitor.jar:lib/commons-lang-2.4.jar:lib/commons-logging-1.1.1.jar:lib/machineagent.jar:lib/mysql-connector-java-5.1.17-bin.jar</classpath>
            <impl-class>com.singularity.ee.agent.systemagent.monitors.ArbitrarySqlMonitor</impl-class>
        </java-task>
```

(Note that I have used MySQL-specific settings for the driver class, driver JAR, and JDBC URL.)
        
When this extension runs the query and processes the results, it will create new metrics in your Metric Browser as follows:

    - Application Infrastructure
      - MyTier
        - Custom Metrics
          - SQL Monitor
            - ACME Book Store
                Node Count
                Metric Count
            - Bundy Shoes
                Node Count
                Metric Count
            - Big Deal Retail
                Node Count
                Metric Count
              Connection Time (ms)
              Execution Time (ms)
              Rows Returned

## Support ##

For any questions or feature requests, please contact the [AppDynamics Center of Excellence][].

**Version:** 1.0  
**Controller Compatibility:** 3.6 or later  
**Last Updated:** 05-Apr-2014  
**Author:** Todd Radel


## Contributing ##

Always feel free to fork and contribute any changes directly via [GitHub][].


## Community ##

Find out more at our [Community][] site.


------------------------------------------------------------------------------

## Release Notes ##

### Version 1.0
 * Initial release.


[GitHub]: https://github.com/Appdynamics/arbitrary-sql-monitoring-extension
[Community]: http://community.appdynamics.com
[AppDynamics Center of Excellence]: mailto:ace-request@appdynamics.com