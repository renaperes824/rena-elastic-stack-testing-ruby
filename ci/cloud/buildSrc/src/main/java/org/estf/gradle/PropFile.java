package org.estf.gradle;

import java.io.File;

/**
 * Cloud properties file
 *
 * @author: Liza Dayoub
 */
public class PropFile {
    public static String getFilename(String clusterId) {
        String workspaceDir = System.getenv("WORKSPACE");
        if (workspaceDir == null) {
            workspaceDir = new File("").getAbsoluteFile().toString(); 
        }
        File dir = new File(workspaceDir);
        boolean isDirectory = dir.isDirectory();
        if (! isDirectory) {
            throw new Error("Environment WORKSPACE is required, not a dir: " + workspaceDir);
        }
        String filename = workspaceDir + '/' + clusterId + ".properties";
        return filename;
    }
}
