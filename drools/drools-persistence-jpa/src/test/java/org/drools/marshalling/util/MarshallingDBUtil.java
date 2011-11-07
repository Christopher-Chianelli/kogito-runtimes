package org.drools.marshalling.util;

/*
 * Copyright 2011 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import static org.drools.persistence.util.PersistenceUtil.*;
import static org.drools.runtime.EnvironmentName.ENTITY_MANAGER_FACTORY;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Table;

import org.drools.persistence.util.PersistenceUtil;

import bitronix.tm.resource.jdbc.PoolingDataSource;

public class MarshallingDBUtil {

    protected static String MARSHALLING_TEST_DB = "marshalling/testData";
    protected static final String MARSHALLING_BASE_DB = "marshalling/baseData-current";

    protected static boolean clearMarshallingTestDb = true;
    
    /**
     * This method is necessary in order to setup the infrastructure to save, retrieve and compare
     *  the marshalled data generated by Drools/jBPM. 
     * <p/>
     * This method does the following:<ul>
     * <li>Find the (absolute) path of the marshalling test database (which stores marshalled data generated during tests).</li>
     * <li>If this is the first time the test db is being used, delete and recreate the test db for this test run.</li>
     * </ul> 
     * <i>Note</i>: we find the database in src/test/resources -- NOT in target/test-classes/.. or whichever 
     * folder your IDE/build system might copy the database to.
     * <p/>
     * @param jdbcProps The JDBC (database) properties.
     * @param testClass The class of the test being run (that will generate marshalled data).
     * @return A Sting containing the URL (in src/test/resources) of the database.
     */
    public static String initializeTestDb(Properties jdbcProps, Class<?> testClass) { 
        Object makeBaseDb = jdbcProps.getProperty("makeBaseDb"); 
        if( "true".equals(makeBaseDb) ) { 
            MARSHALLING_TEST_DB = MARSHALLING_BASE_DB;
            MarshallingTestUtil.DO_NOT_COMPARE_MAKING_BASE_DB = true;
        }
        
        String dbPath = generatePathToTestDb(testClass);
        
        if( clearMarshallingTestDb ) {
            clearMarshallingTestDb = false;
            URL dbUrl = Object.class.getResource("/" + MARSHALLING_TEST_DB + ".h2.db");
            deleteTestDatabase(dbUrl, dbPath);
            createMarshallingTestDatabase(dbPath, jdbcProps.getProperty("driverClassName"));
        }
               
        String jdbcURLBase = jdbcProps.getProperty("url");
        return jdbcURLBase + dbPath;
    }
   
    /**
     * This method constructs the path to the database and ensures that the 
     *  file that the path refers to exists.
     * @param testClass The class of the test doing this, in order to access the classLoader/resources.
     * @return A String containg the absolute URL/path of the test DB.
     */
    protected static String generatePathToTestDb(Class<?> testClass) { 
        URL classUrl = testClass.getResource(testClass.getSimpleName() + ".class");
        String projectPath = classUrl.getPath().replaceFirst("target.*", "");
        String resourcesPath = projectPath + "src/test/resources/";
        new File(resourcesPath).mkdirs();
        new File(resourcesPath + "marshalling").mkdir();
        String dbPath = resourcesPath + MARSHALLING_TEST_DB;
        return dbPath;
    }
    
    /**
     * This method quickly creates a H2 database: a direct JDBC connection is used for this.
     * <p/>
     * @param dbPath The path to the database.
     * @param driverClass The name of the JDBC driver class.
     */
    private static void createMarshallingTestDatabase(String dbPath, String driverClass) { 
        try { 
            Class.forName(driverClass);
            Connection conn = DriverManager.getConnection("jdbc:h2:"+dbPath);
            conn.setAutoCommit(true);
        
            Statement stat = conn.createStatement();
            String dropTableQuery = "drop table if exists " + getTableName(MarshalledData.class);
            stat.executeUpdate(dropTableQuery);
            
            conn.close();
        }
        catch (Exception e) {
            e.printStackTrace();
            fail( "Unable to create marshalling database: " + dbPath);
        }
    }

    /**
     * This method deletes the test database file.
     * @param dbUrl 
     * @param dbPath
     */
    private static void deleteTestDatabase(URL dbUrl, String dbPath) { 
        if( dbUrl != null ) { 
            new File(dbUrl.getPath()).delete();
        }
        new File(dbPath + ".h2.db").delete();
    }
    
    
    /**
     * This method uses reflection to get the name of the table used for an entity.
     * @param dataClass The class for which we want the table name. 
     * @return A String containing the name of the table for the given class.
     */
    private static String getTableName(Class<?> dataClass) { 
        String tableName = null;
        Annotation [] anno = dataClass.getDeclaredAnnotations();
        for( int i = 0; i < anno.length; ++i ) { 
            Class<?> annoClass = anno[i].annotationType();
            if( annoClass.equals(Table.class) )  { 
                Method [] annoMethod = annoClass.getMethods();
                int a = 0;
                while( a < annoMethod.length && ! annoMethod[a].getName().equals("name") ) {
                  ++a; 
                }
                try { 
                    tableName = (String) annoMethod[a].invoke(anno[i]);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    fail( "Unable to generate HQL query - could not get table name: " + e.getMessage() );
                }
            }
        }
        
        if( tableName == null ) { 
            tableName = dataClass.getName();
            tableName = tableName.substring(tableName.lastIndexOf('.')+1).toLowerCase();
        }
        return tableName;
    }

    public static HashMap<String, Object> initializeMarshalledDataEMF(String persistenceUnitName, Class<?> testClass, boolean useBaseDb) { 
        return initializeMarshalledDataEMF(persistenceUnitName, testClass, useBaseDb, null );
    }
    
    protected static String [] getListOfBaseDbVers(Class<?> testClass) { 
        String [] versions;
        ArrayList<String> versionList = new ArrayList<String>();
        
        String path = generatePathToTestDb(testClass);
        path = path.replace(File.separatorChar + "testData", "");
        File marshallingDir = new File(path);
        
        FilenameFilter baseDatafilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("baseData");
            }
        };
        String [] dbFiles = marshallingDir.list(baseDatafilter);
        
        assertTrue("No files found in marshalling directory!", dbFiles != null && dbFiles.length > 0 );
        for(int i = 0; i < dbFiles.length; ++i ) { 
            String ver = dbFiles[i];
            ver = ver.replace(".h2.db", "");
            ver = ver.replace("baseData", "");
            ver = ver.replace("-", "");
            versionList.add(ver);
        } 
        versions = new String [versionList.size()];
        for( int v = 0; v < versions.length; ++v ) { 
           versions[v] = versionList.get(v); 
        }
        return versions;
    }

    /**
     * This method initializes an EntityManagerFactory with a connection to the base (marshalled) data DB. 
     * This database stores the marshalled data that we expect (for a given drools/jbpm version).
     * @param persistenceUnitName The persistence unit name.
     * @param testClass The class of the (local) unit test running.
     * @return A HashMap<String, Object> containg the datasource and entity manager factory.
     */
    public static HashMap<String, Object> initializeMarshalledDataEMF(String persistenceUnitName, Class<?> testClass, 
            boolean useBaseDb, String baseDbVer ) { 
        HashMap<String, Object> context = new HashMap<String, Object>();
        
        Properties dsProps = PersistenceUtil.getDatasourceProperties();
        String driverClass = dsProps.getProperty("driverClassName");
        if ( ! driverClass.startsWith("org.h2")) {
            return null;
        }
    
        String tempDbPath = generatePathToTestDb(testClass);
        if( useBaseDb ) { 
            tempDbPath = tempDbPath.replace(MARSHALLING_TEST_DB, MARSHALLING_BASE_DB);
            String thisDbPath = tempDbPath;
            if( baseDbVer != null && baseDbVer.length() > 0) { 
                thisDbPath = thisDbPath.replace("current", baseDbVer);
            }
            tempDbPath = tempDbPath.replace("-current", "");
            copyH2DbFile(thisDbPath, tempDbPath );
        }
        
        String jdbcURLBase = dsProps.getProperty("url");
        String jdbcUrl =  jdbcURLBase + tempDbPath;
    
        // Setup the datasource
        PoolingDataSource ds1 = setupPoolingDataSource(dsProps);
        ds1.getDriverProperties().setProperty("url", jdbcUrl);
        ds1.init();
        context.put(DATASOURCE, ds1);
    
        // Setup persistence
        Properties overrideProperties = new Properties();
        overrideProperties.setProperty("hibernate.connection.url", jdbcUrl);
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(persistenceUnitName, overrideProperties);
        context.put(ENTITY_MANAGER_FACTORY, emf);
        
        return context;
    }
    
    
    private static void copyH2DbFile(String source, String dest) {
        source += ".h2.db";
        dest += ".h2.db";
        try {
            File sourceFile = new File(source);
            File destFile = new File(dest);
            InputStream in = new FileInputStream(sourceFile);
        
            OutputStream out = new FileOutputStream(destFile);

            byte[] buf = new byte[1024];
            for( int len = in.read(buf); len  > 0; len = in.read(buf) ){
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        }
        catch( Exception e ){
            fail("Unable to copy " + source + " to " + dest + ": " + e.getMessage() );
        }
    } 
}
