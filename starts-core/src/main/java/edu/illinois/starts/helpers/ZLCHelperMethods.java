/*
 * Copyright (c) 2015 - Present. The STARTS Team. All Rights Reserved.
 */

package edu.illinois.starts.helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.illinois.starts.constants.StartsConstants;
import edu.illinois.starts.data.ZLCData;
import edu.illinois.starts.data.ZLCFileContent;
import edu.illinois.starts.data.ZLCFormat;
import edu.illinois.starts.smethods.MethodLevelStaticDepsBuilder;
import edu.illinois.starts.util.ChecksumUtil;
import edu.illinois.starts.util.Logger;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Utility methods for dealing with the .zlc format.
 */
public class ZLCHelperMethods implements StartsConstants {
    private static final Logger LOGGER = Logger.getGlobal();
    private static final String NOEXISTING_ZLCFILE_FIRST_RUN = "@NoExistingZLCFile. First Run?";

    // This method creates a file that stores the method-level dependency graph.
    public static void writeZLCFile(Map<String, Set<String>> methodsToTests, Map<String, String> checksumsMap,
            ClassLoader loader,
            String artifactsDir, Set<String> unreached, boolean useThirdParty,
            ZLCFormat format) {
        long start = System.currentTimeMillis();
        LOGGER.log(Level.FINE, "ZLC format: " + format.toString());
        ZLCFileContent zlc = createZLCFileData(methodsToTests, checksumsMap, loader, useThirdParty, format);

        Writer.writeToFile(zlc, METHODS_TEST_DEPS_ZLC_FILE, artifactsDir);
        long end = System.currentTimeMillis();
        LOGGER.log(Level.FINE, "[PROFILE] updateForNextRun(updateZLCFile): " + Writer.millsToSeconds(end - start));
    }

    // This method creates a file that stores 2 things:
    // 1. method-level dependency graph used in hybrid analysis.
    // 2. class-level checksums used in hybrid analysis.
    public static void writeZLCFileHybrid(Map<String, Set<String>> methodToTests, Map<String, String> methodsChecksums,
            Map<String, String> classesChecksums, ClassLoader loader,
            String artifactsDir, Set<String> unreached, boolean useThirdParty,
            ZLCFormat format) {
        long start = System.currentTimeMillis();
        LOGGER.log(Level.FINE, "ZLC format: " + format.toString());
        ZLCFileContent zlc = createZLCFileData(methodToTests, methodsChecksums, loader, useThirdParty, format);
        Writer.writeToFile(zlc, METHODS_TEST_DEPS_ZLC_FILE, artifactsDir);

        zlc = createZLCFileDataClasses(classesChecksums, loader, useThirdParty, format);

        Writer.writeToFile(zlc, CLASSES_ZLC_FILE, artifactsDir);
        long end = System.currentTimeMillis();
        LOGGER.log(Level.FINE, "[PROFILE] updateForNextRun(updateZLCFile): " + Writer.millsToSeconds(end - start));
    }

    // This method computes the class-level checksums.
    public static ZLCFileContent createZLCFileDataClasses(
            Map<String, String> checksumMap,
            ClassLoader loader,
            boolean useJars,
            ZLCFormat format) {

        long start = System.currentTimeMillis();
        List<ZLCData> zlcData = new ArrayList<>();
        ArrayList<String> classList = new ArrayList<>(checksumMap.keySet());

        for (Map.Entry<String, String> entry : checksumMap.entrySet()) {
            String klasName = entry.getKey();
            String klasChecksum = entry.getValue();

            String klas = ChecksumUtil.toClassName(klasName);
            URL url = loader.getResource(klas);
            String extForm = url.toExternalForm();
            if (ChecksumUtil.isWellKnownUrl(extForm) || (!useJars && extForm.startsWith("jar:"))) {
                continue;
            }
            String classURL = url.toString();
            URL newUrl = null;
            try {
                newUrl = new URL(classURL);
            } catch (MalformedURLException malformedURLException) {
                throw new RuntimeException(malformedURLException);
            }
            zlcData.add(new ZLCData(newUrl, klasChecksum, format, new HashSet<>(), null));
        }

        long end = System.currentTimeMillis();
        LOGGER.log(Level.FINEST, "[TIME]CREATING ZLC FILE: " + (end - start) + MILLISECOND);
        return new ZLCFileContent(classList, zlcData, format);
    }

    // This method computes the method-level checksums.
    public static ZLCFileContent createZLCFileData(
            Map<String, Set<String>> methodToTests,
            Map<String, String> checksumMap,
            ClassLoader loader,
            boolean useJars,
            ZLCFormat format) {

        long start = System.currentTimeMillis();
        List<ZLCData> zlcData = new ArrayList<>();
        ArrayList<String> methodList = new ArrayList<>(methodToTests.keySet());

        for (Map.Entry<String, String> entry : checksumMap.entrySet()) {
            String methodPath = entry.getKey();
            String methodChecksum = entry.getValue();
            Set<String> deps = methodToTests.getOrDefault(methodPath, new HashSet<>());

            String klas = ChecksumUtil.toClassName(methodPath.split("#")[0]);
            URL url = loader.getResource(klas);
            String extForm = url.toExternalForm();
            if (ChecksumUtil.isWellKnownUrl(extForm) || (!useJars && extForm.startsWith("jar:"))) {
                continue;
            }
            String classURL = url.toString();
            URL newUrl = null;
            try {
                newUrl = new URL(classURL + "#" + methodPath.split("#")[1]);
            } catch (MalformedURLException malformedURLException) {
                throw new RuntimeException(malformedURLException);
            }
            zlcData.add(new ZLCData(newUrl, methodChecksum, format, deps, null));
        }

        long end = System.currentTimeMillis();
        LOGGER.log(Level.FINEST, "[TIME]CREATING ZLC FILE: " + (end - start) + MILLISECOND);
        return new ZLCFileContent(methodList, zlcData, format);
    }

    public static void updateZLCFile(Map<String, Set<String>> testDeps, ClassLoader loader,
            String artifactsDir, Set<String> unreached, boolean useThirdParty,
            ZLCFormat format) {
        long start = System.currentTimeMillis();
        LOGGER.log(Level.FINE, "ZLC format: " + format.toString());
        ZLCFileContent zlc = createZLCData(testDeps, loader, useThirdParty, format);
        Writer.writeToFile(zlc, METHODS_TEST_DEPS_ZLC_FILE, artifactsDir);
        long end = System.currentTimeMillis();
        LOGGER.log(Level.FINE, "[PROFILE] updateForNextRun(updateZLCFile): " + Writer.millsToSeconds(end - start));
    }

    public static ZLCFileContent createZLCData(
            Map<String, Set<String>> methodToMethods,
            ClassLoader loader,
            boolean useJars,
            ZLCFormat format) {

        long start = System.currentTimeMillis();
        List<ZLCData> zlcData = new ArrayList<>();

        ArrayList<String> methodList = new ArrayList<>(methodToMethods.keySet()); // all tests

        for (Map.Entry<String, Set<String>> entry : methodToMethods.entrySet()) {
            String methodPath = entry.getKey();
            Set<String> deps = entry.getValue();

            String klas = ChecksumUtil.toClassName(methodPath.split("#")[0]);
            String methodName = methodPath.split("#")[1].replace("()", "");

            URL url = loader.getResource(klas);

            String path = url.getPath();
            ClassNode node = new ClassNode(Opcodes.ASM5);
            ClassReader reader = null;
            try {
                reader = new ClassReader(new FileInputStream(path));
            } catch (IOException ioException) {
                LOGGER.log(Level.INFO, "error reading class file: " + path);
                continue;
            }

            String methodChecksum = null;
            reader.accept(node, ClassReader.SKIP_DEBUG);
            List<MethodNode> methods = node.methods;
            for (MethodNode method : methods) {
                // methodName is from the generated graph
                // method.name is from method visitor (ASM) -> does not have signatures
                // Append the parameter signature
                String method1 = appendParametersToMethodName(method);
                if (!method1.equals(methodName)) {
                    continue;
                }
                String methodContent = printMethodContent(method);
                try {
                    methodChecksum = ChecksumUtil.computeMethodChecksum(methodContent);
                } catch (IOException ioException) {
                    throw new RuntimeException(ioException);
                }
            }

            String extForm = url.toExternalForm();
            if (ChecksumUtil.isWellKnownUrl(extForm) || (!useJars && extForm.startsWith("jar:"))) {
                continue;
            }
            String classURL = url.toString();
            URL newUrl = null;
            try {
                newUrl = new URL(classURL + "#" + methodName);

            } catch (MalformedURLException malformedURLException) {
                throw new RuntimeException(malformedURLException);
            }
            zlcData.add(new ZLCData(newUrl, methodChecksum, format, deps, null));
        }

        long end = System.currentTimeMillis();
        LOGGER.log(Level.FINEST, "[TIME]CREATING ZLC FILE: " + (end - start) + MILLISECOND);
        return new ZLCFileContent(methodList, zlcData, format);
    }

    /*
     * Finds the changedMethods, oldAffectedTests, newMethods, oldClasses,
     * changedClasses
     */
    public static List<Set<String>> getChangedDataHybrid(ClassLoader loader, String artifactsDir, boolean cleanBytes,
            Map<String, String> classesChecksums, String methodFilePath, String classesFilePath) {
        long start = System.currentTimeMillis();

        File zlc = new File(artifactsDir, classesFilePath);
        if (!zlc.exists()) {
            LOGGER.log(Level.FINEST, NOEXISTING_ZLCFILE_FIRST_RUN);
            return null;
        }

        Set<String> changedClasses = new HashSet<>();
        Set<String> oldClasses = new HashSet<>();

        try {
            List<String> zlcLines = Files.readAllLines(zlc.toPath(), Charset.defaultCharset());
            String space = WHITE_SPACE;
            zlcLines.remove(0);

            // on PLAIN_TEXT, testsCount+1 will starts from 0
            for (int i = 0; i < zlcLines.size(); i++) {

                String line = zlcLines.get(i);
                String[] parts = line.split(space);
                String classURL = parts[0];
                String oldCheckSum = parts[1];

                // convert classURL to class name
                String className = convertPath(classURL);
                String newChecksum = classesChecksums.get(className);
                oldClasses.add(className);
                if (oldCheckSum.equals(newChecksum)) {
                    continue;
                } else {
                    changedClasses.add(className);
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        Set<String> newClasses = new HashSet<>(classesChecksums.keySet());
        newClasses.removeAll(oldClasses);

        zlc = new File(artifactsDir, methodFilePath);
        if (!zlc.exists()) {
            LOGGER.log(Level.FINEST, NOEXISTING_ZLCFILE_FIRST_RUN);
            return null;
        }

        Map<String, String> methodsChecksums = MethodLevelStaticDepsBuilder
                .getMethodsChecksumsForClasses(changedClasses, loader);

        Set<String> changedMethods = new HashSet<>();
        Set<String> newMethods = new HashSet<>();
        Set<String> affectedTestClasses = new HashSet<>();
        Map<String, String> oldMethodsChecksums = new HashMap<>();
        Map<String, Set<String>> oldMethodsDeps = new HashMap<>();
        try {
            List<String> zlcLines = Files.readAllLines(zlc.toPath(), Charset.defaultCharset());
            String space = WHITE_SPACE;
            zlcLines.remove(0);

            // on PLAIN_TEXT, testsCount+1 will starts from 0
            for (int i = 0; i < zlcLines.size(); i++) {

                String line = zlcLines.get(i);
                String[] parts = line.split(space);
                // classURL#methodname
                String stringURL = parts[0];
                String classURL = stringURL.split("#")[0];
                String methodName = stringURL.split("#")[1];
                String oldCheckSum = parts[1];
                Set<String> deps;
                deps = parts.length == 3 ? fromCSV(parts[2]) : new HashSet<>(); // Fields should be returned

                // convert classURL to class name
                String className = convertPath(classURL);
                String methodPath = className + "#" + methodName;
                oldMethodsDeps.put(methodPath, deps);
                oldMethodsChecksums.put(methodPath, oldCheckSum);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        for (String methodPath : methodsChecksums.keySet()) {
            String newChecksum = methodsChecksums.get(methodPath);
            String oldChecksum = oldMethodsChecksums.get(methodPath);

            // If the old checksum is null, it means that this method is a new method.
            if (oldChecksum == null) {
                newMethods.add(methodPath);
                continue;
            }
            if (!oldChecksum.equals(newChecksum)) {
                changedMethods.add(methodPath);
                affectedTestClasses.addAll(oldMethodsDeps.getOrDefault(methodPath, new HashSet<>()));
            }
        }

        // Updateing methods checksums
        for (String methodPath : oldMethodsChecksums.keySet()) {
            String newChecksum = methodsChecksums.get(methodPath);
            String oldChecksum = oldMethodsChecksums.get(methodPath);
            if (newChecksum == null) {
                methodsChecksums.put(methodPath, oldChecksum);
            }
        }

        long end = System.currentTimeMillis();
        List<Set<String>> result = new ArrayList<>();
        result.add(changedMethods);
        result.add(newMethods);
        result.add(affectedTestClasses);
        result.add(changedClasses);
        result.add(newClasses);
        result.add(oldClasses);
        LOGGER.log(Level.FINEST, TIME_COMPUTING_NON_AFFECTED + (end - start) + MILLISECOND);
        return result;
    }

    /*
     * Finds the changedMethods, oldAffectedTests, newMethods, oldClasses,
     * changedClasses
     */
    public static List<Set<String>> getChangedDataMethods(String artifactsDir, boolean cleanBytes,
            Map<String, String> methodsChecksums, String filePath) {
        long start = System.currentTimeMillis();

        File zlc = new File(artifactsDir, filePath);
        if (!zlc.exists()) {
            LOGGER.log(Level.FINEST, NOEXISTING_ZLCFILE_FIRST_RUN);
            return null;
        }

        Set<String> changedMethods = new HashSet<>();
        Set<String> affectedTests = new HashSet<>();
        Set<String> oldClasses = new HashSet<>();
        Set<String> changedClasses = new HashSet<>();
        Set<String> newMethods = new HashSet<>(methodsChecksums.keySet());

        try {
            List<String> zlcLines = Files.readAllLines(zlc.toPath(), Charset.defaultCharset());
            String space = WHITE_SPACE;
            zlcLines.remove(0);

            // on PLAIN_TEXT, testsCount+1 will starts from 0
            for (int i = 0; i < zlcLines.size(); i++) {

                String line = zlcLines.get(i);
                String[] parts = line.split(space);
                // classURL#methodname
                String stringURL = parts[0];
                String classURL = stringURL.split("#")[0];
                String methodName = stringURL.split("#")[1];
                String oldCheckSum = parts[1];
                Set<String> deps;
                deps = parts.length == 3 ? fromCSV(parts[2]) : new HashSet<>(); // Fields should be returned

                // convert classURL to class name
                String className = convertPath(classURL);
                String methodPath = className + "#" + methodName;
                String newChecksum = methodsChecksums.get(methodPath);
                oldClasses.add(className);
                newMethods.remove(methodPath);
                if (oldCheckSum.equals(newChecksum)) {
                    continue;
                } else {
                    changedMethods.add(methodPath);
                    affectedTests.addAll(deps);
                    changedClasses.add(className);
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        long end = System.currentTimeMillis();
        List<Set<String>> result = new ArrayList<>();
        for (String method : newMethods) {
            changedClasses.add(convertPath(method.split("#")[0]));
        }
        result.add(changedMethods);
        result.add(newMethods);
        result.add(affectedTests);
        result.add(oldClasses);
        result.add(changedClasses);
        LOGGER.log(Level.FINEST, TIME_COMPUTING_NON_AFFECTED + (end - start) + MILLISECOND);
        return result;
    }

    public static String convertPath(String fullPath) {
        String[] parts = fullPath.split("/");
        int index = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("classes") || parts[i].equals("test-classes")) {
                index = i + 1;
                break;
            }
        }
        String result = "";
        for (int i = index; i < parts.length; i++) {
            result += parts[i];
            if (i != parts.length - 1) {
                result += "/";
            }
        }
        return result.replace(".class", "");
    }

    private static Set<String> fromCSV(String tests) {
        return new HashSet<>(Arrays.asList(tests.split(COMMA)));
    }

    public static String printMethodContent(MethodNode node) {
        Printer printer = new Textifier(Opcodes.ASM5) {
            @Override
            public void visitLineNumber(int line, Label start) {
            }
        };
        TraceMethodVisitor methodPrinter = new TraceMethodVisitor(printer);
        node.accept(methodPrinter);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        // include the access code in case of access code changes
        String methodContent = node.access + "\n" + node.signature + "\n" + node.name + "\n" + node.desc + "\n"
                + sw.toString();
        return methodContent;
    }

    public static String appendParametersToMethodName(MethodNode methodNode) {
        String method1 = methodNode.name;
        String parameters = methodNode.desc;
        Pattern pattern = Pattern.compile("\\(.*?\\)");
        Matcher matcher = pattern.matcher(parameters);
        if (matcher.find()) {
            String extracted = matcher.group();
            // Handle the case where there are no parameters
            if (extracted.equals("()")) {
                return method1;
            }

            method1 += extracted;
            return method1;
        }
        return method1;
    }
}
