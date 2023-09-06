package edu.illinois.starts.smethods;
/*
 * Copyright (c) 2015 - Present. The STARTS Team. All Rights Reserved.
 */



 import static org.junit.Assert.assertEquals;
 import static org.junit.Assert.assertTrue;
 
 import java.io.BufferedReader;
 import java.io.File;
 import java.io.IOException;
 import java.nio.charset.Charset;
 import java.nio.charset.StandardCharsets;
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.nio.file.Paths;
import java.sql.Array;
import java.util.ArrayList;
 import java.util.HashMap;
 import java.util.HashSet;
 import java.util.List;
 import java.util.Map;
 import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;




import edu.emory.mathcs.backport.java.util.Arrays;
import edu.emory.mathcs.backport.java.util.Collections;
import edu.illinois.starts.smethods.MethodLevelStaticDepsBuilder;

 import org.junit.AfterClass;
 import org.junit.BeforeClass;
 import org.junit.Before;
 import org.junit.Test;
 
public class MethodLevelStaticDepsBuilderTest {
    //This is for testing some methods in the new smethod file 
    private static  MethodLevelStaticDepsBuilder TestBuilder;
    public static final String TEST_EMPTY_FILE_PATH = "emptytest.txt";
    public static final String TEST_EDGE_FILE_PATH = "edgetest.txt";
    public static File emptyfile;
    public static File edgefile;


    @BeforeClass
    public static void setUp() throws IOException {
        // Add a mock dependency graph
       Map<String, Set<String>> methodDependencyGraph = new HashMap<>();
        // TestClass#MethodA depends on TestClass#MethodB and TestClass#MethodC
        methodDependencyGraph.put("TestClass#MethodA", new HashSet<>(Arrays.asList(new String[]{"TestClass#MethodB", "TestClass#MethodC"})));

        // TestClass#MethodB depends on AnotherTestClass#MethodD
        methodDependencyGraph.put("TestClass#MethodB", new HashSet<>(Arrays.asList(new String[]{"AnotherTestClass#MethodD"})));

        // TestClass#MethodC has no dependencies
        methodDependencyGraph.put("TestClass#MethodC", new HashSet<>());

        // AnotherTestClass#MethodD depends on AnotherTestClass#MethodE
        methodDependencyGraph.put("AnotherTestClass#MethodD", new HashSet<>(Arrays.asList(new String[]{"AnotherTestClass#MethodE"})));

        // AnotherTestClass#MethodE has no dependencies
        methodDependencyGraph.put("AnotherTestClass#MethodE", new HashSet<>());

        MethodLevelStaticDepsBuilder.methodDependencyGraph = methodDependencyGraph;

        Map<String, Set<String>> methodName2MethodNames = new HashMap<>();

        //TestClass#MethodA invokes TestClass#MethodB and TestClass#MethodC.
        //TestClass#MethodB invokes AnotherTestClass#MethodD.
        //AnotherTestClass#MethodD invokes AnotherTestClass#MethodE.

        methodName2MethodNames.put("TestClass#MethodA", new HashSet<>(Arrays.asList(new String[]{"TestClass#MethodB", "TestClass#MethodC"})));
        methodName2MethodNames.put("TestClass#MethodB",new HashSet<>(Arrays.asList(new String[]{"AnotherTestClass#MethodD"})));
        methodName2MethodNames.put("TestClass#MethodC",new HashSet<>());
        methodName2MethodNames.put("AnotherTestClass#MethodD", new HashSet<>(Arrays.asList(new String[]{"AnotherTestClass#MethodE"})));
        methodName2MethodNames.put("AnotherTestClass#MethodE", new HashSet<>());

        MethodLevelStaticDepsBuilder.methodName2MethodNames = methodName2MethodNames;

    }

    @AfterClass
    public static void cleanUp() {
        MethodLevelStaticDepsBuilder.methodDependencyGraph = new HashMap<>();
    }

 

    @Test
    public void testGetDepsHelper() {
        // Test the getDepsHelper method
        // Goal: Ensure that the dependencies helper retrieves dependencies correctly

        Set<String> expectedMethods = new HashSet<>(Arrays.asList(new String[]{"TestClass#MethodA", "TestClass#MethodC", "TestClass#MethodB","AnotherTestClass#MethodD", "AnotherTestClass#MethodE"}));
        Set<String> resultMethods = MethodLevelStaticDepsBuilder.getDepsHelper("TestClass");

        assertEquals(expectedMethods, resultMethods);


    }

    @Test
    public void testGetDepsDFS() {
        // Test the getDepsDFS method
        // Goal: Ensure that the DFS retrieves dependencies correctly
        Set<String> visitedMethods = new HashSet<>();
        MethodLevelStaticDepsBuilder.getDepsDFS("TestClass#MethodA", visitedMethods);

        // Assert that all methods that TestClass#MethodA depends on (directly or indirectly) are visited
        assertTrue(visitedMethods.contains("TestClass#MethodB"));
        assertTrue(visitedMethods.contains("TestClass#MethodC"));
        assertTrue(visitedMethods.contains("AnotherTestClass#MethodD"));
        assertTrue(visitedMethods.contains("AnotherTestClass#MethodE"));
    }

    @Test
    public void testGetTestClassMethodDeps() {
        // Test the getAnotherTestClass#MethodDeps method
        // Goal: Ensure that the method dependencies are retrieved correctly

        Set<String> Deps = MethodLevelStaticDepsBuilder.getMethodDeps("TestClass#MethodA");
        Set<String> expectedDeps = new HashSet<>(Arrays.asList(new String[]{"TestClass#MethodA", "TestClass#MethodB", "TestClass#MethodC", "AnotherTestClass#MethodD", "AnotherTestClass#MethodE"}));

        assertEquals(expectedDeps, Deps);

    }






    @Test
    public void testInvertMap() {
        // Test the invertMap method
        // Goal: Ensure that the map inversion works correctly
        // Setup the original map
        /*
         * A -> X,Y
         * B -> Y,Z
         */
        Map<String, Set<String>> originalMap = new HashMap<>();
        originalMap.put("A", new HashSet<>(Arrays.asList(new String[]{"X", "Y"})));
        originalMap.put("B", new HashSet<>(Arrays.asList(new String[]{"Y", "Z"})));

        // Call the method to invert the map
        Map<String, Set<String>> invertedMap = MethodLevelStaticDepsBuilder.invertMap(originalMap);

        // Expected inverted map
        /*
         * X->A
         * Y->A,B
         * Z->B
         */
        Map<String, Set<String>> expectedMap = new HashMap<>();
        expectedMap.put("X", new HashSet<>(Arrays.asList(new String[]{ "A"})));
        expectedMap.put("Y", new HashSet<>(Arrays.asList(new String[]{"A", "B"})));
        expectedMap.put("Z", new HashSet<>(Arrays.asList(new String[]{"B"})));

        // Assert that the inverted map matches the expected map
        assertEquals(expectedMap, invertedMap);
    }
    

    @Test
    public void testAddReflexiveClosure() {
        // Test the addReflexiveClosure method
        // Goal: Ensure that the reflexive closure is added correctly
        /* Before 
         * A -> B,C
         * B -> C
         * C
         */
        Map<String, Set<String>> testMap = new HashMap<>();
        testMap.put("methodA", Stream.of("methodB", "methodC").collect(Collectors.toSet()));
        testMap.put("methodB", Stream.of("methodC").collect(Collectors.toSet()));
        testMap.put("methodC", new HashSet<>());

        MethodLevelStaticDepsBuilder.addReflexiveClosure(testMap);
        /* After execution should be:
         * A -> A,B,C
         * B -> B,C
         * C -> C
         */

        Map<String, Set<String>> expectedMap = new HashMap<>();
        expectedMap.put("methodA", Stream.of("methodA", "methodB", "methodC").collect(Collectors.toSet()));
        expectedMap.put("methodB", Stream.of("methodB", "methodC").collect(Collectors.toSet()));
        expectedMap.put("methodC", Stream.of("methodC").collect(Collectors.toSet()));

        assertEquals(expectedMap, testMap);

    }



    
}
