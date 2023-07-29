/*
 * Copyright (c) 2015 - Present. The STARTS Team. All Rights Reserved.
 */

package edu.illinois.starts.jdeps;

import java.util.*;
import java.util.logging.Level;

import edu.illinois.starts.constants.StartsConstants;
import edu.illinois.starts.helpers.Writer;
import edu.illinois.starts.helpers.ZLCHelperMethods;
import edu.illinois.starts.smethods.MethodLevelStaticDepsBuilder;
import edu.illinois.starts.util.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.surefire.booter.Classpath;

// import static edu.illinois.starts.smethods.MethodLevelStaticDepsBuilder.buildMethodsGraph;
// import static edu.illinois.starts.smethods.MethodLevelStaticDepsBuilder.methodName2MethodNames;

@Mojo(name = "methods", requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.TEST_COMPILE)
public class MethodsMojo extends DiffMojo {

    private Logger logger;
    private Set<String> impacted;
    private Set<String> changed;
    private Set<String> affected;

    @Parameter(property = "updateMethodsChecksums", defaultValue = FALSE)
    private boolean updateMethodsChecksums;

    public void setUpdateMethodsChecksums(boolean updateChecksums) {
        this.updateMethodsChecksums = updateChecksums;
    }

    public void execute() throws MojoExecutionException {
        Logger.getGlobal().setLoggingLevel(Level.parse(loggingLevel));
        logger = Logger.getGlobal();
        setIncludesExcludes(); 

        // Build method level static dependencies
        try {
            MethodLevelStaticDepsBuilder.buildMethodsGraph();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<Set<String>> data = ZLCHelperMethods.getChangedData(getArtifactsDir(), cleanBytes);

        changed = data == null ? new HashSet<String>() : data.get(0);
        affected = data == null ? new HashSet<String>() : data.get(1);
        
        // If it is first run with update methods checksums as true, then all methods are impacted.
        impacted = (updateMethodsChecksums && data == null) ? getAllMethods() : findImpactedMethods(affected);
        

        logger.log(Level.FINEST, "CHANGED: " + changed.toString());
        logger.log(Level.FINEST, "IMPACTED: " + impacted.toString());

        // Optionally update methods-deps.zlc
        if (updateMethodsChecksums) {
            this.updateForNextRun(null);
        }

        Writer.writeToFile(changed, "changed-methods", getArtifactsDir());
        Writer.writeToFile(impacted, "impacted-methods", getArtifactsDir());
    }

    protected void updateForNextRun(Set<String> nonAffected) throws MojoExecutionException {
        Classpath sfClassPath = getSureFireClassPath();
        ClassLoader loader = createClassLoader(sfClassPath);
        ZLCHelperMethods.updateZLCFile(MethodLevelStaticDepsBuilder.methodName2MethodNames, loader, getArtifactsDir(),
                nonAffected, useThirdParty, zlcFormat);
    }

    private Set<String> findImpactedMethods(Set<String> affectedMethods) {
        Set<String> impactedMethods = new HashSet<>(affectedMethods);
        Map<String, Set<String>> graph = MethodLevelStaticDepsBuilder.methodName2MethodNames;
        for (String method : affectedMethods) {
            if (graph.containsKey(method)) {
                impactedMethods.addAll(graph.get(method));
            }
        }
        return impactedMethods;
    }

    private Set<String> getAllMethods() {
        Set<String> allMethods = new HashSet<>();
        for (Set<String> methods : MethodLevelStaticDepsBuilder.methodName2MethodNames.values()) {
            allMethods.addAll(methods);
        }
        return allMethods;
    }
}