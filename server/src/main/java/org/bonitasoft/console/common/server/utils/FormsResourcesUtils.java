/**
 * Copyright (C) 2012 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.bonitasoft.console.common.server.utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.bonitasoft.console.common.server.preferences.constants.WebBonitaConstantsUtils;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.TenantAPIAccessor;
import org.bonitasoft.engine.api.TenantAdministrationAPI;
import org.bonitasoft.engine.bpm.process.ProcessDefinition;
import org.bonitasoft.engine.bpm.process.ProcessDefinitionNotFoundException;
import org.bonitasoft.engine.business.data.BusinessDataRepositoryException;
import org.bonitasoft.engine.exception.BonitaHomeNotSetException;
import org.bonitasoft.engine.exception.RetrieveException;
import org.bonitasoft.engine.exception.ServerAPIException;
import org.bonitasoft.engine.exception.UnknownAPITypeException;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.engine.session.InvalidSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Anthony Birembaut
 */
public class FormsResourcesUtils {

    /**
     * The forms directory name in the bar
     */
    public final static String FORMS_DIRECTORY_IN_BAR = "resources/forms";

    /**
     * The forms lib directory name in the bar
     */
    public final static String LIB_DIRECTORY_IN_BAR = "lib";

    /**
     * The forms validators directory name in the bar
     */
    public final static String VALIDATORS_DIRECTORY_IN_BAR = "validators";

    /**
     * Process UUID separator
     */
    public final static String UUID_SEPARATOR = "--";

    /**
     * A map used to store the classloaders that are used to load some libraries extracted from the business archive
     */
    private final static Map<Long, ClassLoader> PROCESS_CLASSLOADERS = new HashMap<>();

    /**
     * Util class allowing to work with the BPM engine API
     */
    protected static final BPMEngineAPIUtil bpmEngineAPIUtil = new BPMEngineAPIUtil();

    /**
     * Logger
     */
    protected static final Logger LOGGER = LoggerFactory.getLogger(FormsResourcesUtils.class.getName());

    /**
     * Retrieve the web resources from the business archive and store them in a local directory
     *
     * @param session
     *            the engine API session
     * @param processDefinitionID
     *            the process definition ID
     * @param processDeploymentDate
     *            the process deployment date
     */
    public static synchronized void retrieveApplicationFiles(final APISession session, final long processDefinitionID, final Date processDeploymentDate)
            throws IOException, ProcessDefinitionNotFoundException, InvalidSessionException, RetrieveException, BPMEngineException {

        final ProcessAccessor process = new ProcessAccessor(bpmEngineAPIUtil.getProcessAPI(session));
        final File formsDir = getApplicationResourceDir(session, processDefinitionID, processDeploymentDate);
        if (!formsDir.exists()) {
            formsDir.mkdirs();
        }
        final Map<String, byte[]> formsResources = process.getResources(processDefinitionID, FORMS_DIRECTORY_IN_BAR + "/.*");
        for (final Entry<String, byte[]> formResource : formsResources.entrySet()) {
            final String filePath = formResource.getKey().substring(FORMS_DIRECTORY_IN_BAR.length() + 1);
            final byte[] fileContent = formResource.getValue();
            final File formResourceFile = new File(formsDir.getPath() + File.separator + filePath);
            final File formResourceFileDir = formResourceFile.getParentFile();
            if (!formResourceFileDir.exists()) {
                formResourceFileDir.mkdirs();
            }
            formResourceFile.createNewFile();
            if (fileContent != null) {
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(formResourceFile);
                    fos.write(fileContent);
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (final IOException e) {
                            if (LOGGER.isWarnEnabled()) {
                                LOGGER.warn( "unable to close file output stream for business archive resource " + formResourceFile.getPath(), e);
                            }
                        }
                    }
                }
            }
        }

        final File processApplicationsResourcesDir = FormsResourcesUtils.getApplicationResourceDir(session, processDefinitionID, processDeploymentDate);
        final ClassLoader processClassLoader = createProcessClassloader(processDefinitionID, processApplicationsResourcesDir);
        PROCESS_CLASSLOADERS.put(processDefinitionID, processClassLoader);
    }

    /**
     * Create a classloader for the process
     *
     * @param processDefinitionID
     *            the process definition ID
     * @param processApplicationsResourcesDir
     *            the process application resources directory
     * @return a Classloader
     */
    private static ClassLoader createProcessClassloader(final long processDefinitionID, final File processApplicationsResourcesDir) throws IOException {
        ClassLoader processClassLoader = null;
        try {
            final URL[] librariesURLs = getLibrariesURLs(processApplicationsResourcesDir);
            if (librariesURLs.length > 0) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Creating the classloader for process " + processDefinitionID);
                }
                processClassLoader = new URLClassLoader(librariesURLs, Thread.currentThread().getContextClassLoader());
            }
        } catch (final IOException e) {
            final String message = "Unable to create the class loader for the process's libraries";
             if (LOGGER.isErrorEnabled()) {
                LOGGER.error( message, e);
            }
            throw new IOException(message);
        }
        return processClassLoader;
    }

    /**
     * Get the URLs of the validators' jar and their dependencies
     *
     * @param processApplicationsResourcesDir
     *            the process application resources directory
     * @return an array of URL
     */
    private static URL[] getLibrariesURLs(final File processApplicationsResourcesDir) throws IOException {
        final List<URL> urls = new ArrayList<>();
        final File libDirectory = new File(processApplicationsResourcesDir, FormsResourcesUtils.LIB_DIRECTORY_IN_BAR + File.separator);
        if (libDirectory.exists()) {
            final File[] libFiles = libDirectory.listFiles();
            for (File libFile : libFiles) {
                urls.add(libFile.toURI().toURL());
            }
        }
        final File validatorsDirectory = new File(processApplicationsResourcesDir, FormsResourcesUtils.VALIDATORS_DIRECTORY_IN_BAR + File.separator);
        if (validatorsDirectory.exists()) {
            final File[] validatorsFiles = validatorsDirectory.listFiles();
            for (File validatorsFile : validatorsFiles) {
                urls.add(validatorsFile.toURI().toURL());
            }
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("The validators directory doesn't exists.");
            }
        }
        final URL[] urlArray = new URL[urls.size()];
        urls.toArray(urlArray);
        return urlArray;
    }

    protected static ClassLoader setCorrectHierarchicalClassLoader(ClassLoader processClassLoader, final ClassLoader parentClassLoader) {
        if (processClassLoader == null) {
            processClassLoader = parentClassLoader;
        }
        return processClassLoader;
    }

    /**
     * Retrieve the class loader associated with the process or create it if there is no classloader associated with this process yet
     *
     * @param session
     *            the API session
     * @param processDefinitionID
     *            the process definition ID
     * @return a {@link ClassLoader}, null if the process classloader doesn't exists and couldn't be created
     */
    public ClassLoader getProcessClassLoader(final APISession session, final long processDefinitionID) {
        final File currentBDMFolder = FormsResourcesUtils.getCurrentBDMFolder(session);
        if (PROCESS_CLASSLOADERS.containsKey(processDefinitionID)) {
            // CHECK BDM VERSION AND SEE IF CLASSLOADER IS UP TO DATE
            // IF NO RECREATE THE CLASSLOADER
            if (isClassloaderUpToDateWithCurrentBdm(currentBDMFolder)) {
                return PROCESS_CLASSLOADERS.get(processDefinitionID);
            } else {
                PROCESS_CLASSLOADERS.remove(processDefinitionID);
                cleanBDMFolder(currentBDMFolder);
                FormsResourcesUtils.createAndSaveProcessClassloader(session, processDefinitionID, currentBDMFolder);
                return PROCESS_CLASSLOADERS.get(processDefinitionID);
            }
        }
        FormsResourcesUtils.createAndSaveProcessClassloader(session, processDefinitionID, currentBDMFolder);
        return PROCESS_CLASSLOADERS.get(processDefinitionID);

    }

    protected boolean isClassloaderUpToDateWithCurrentBdm(final File currentBDMFolder) {
        return currentBDMFolder == null || currentBDMFolder.exists();
    }

    protected static ClassLoader createProcessClassloader(final APISession session, final long processDefinitionID) {
        ClassLoader processClassLoader = null;
        try {
            final ProcessDefinition processDefinition = bpmEngineAPIUtil.getProcessAPI(session).getProcessDefinition(processDefinitionID);

            final String processPath = WebBonitaConstantsUtils.getTenantInstance().getFormsWorkFolder() + File.separator;
            final File processDir = new File(processPath, processDefinition.getName() + UUID_SEPARATOR + processDefinition.getVersion());
            if (processDir.exists()) {
                final long lastdeploymentDate = getLastDeploymentDate(processDir);
                final File processApplicationsResourcesDir = new File(processDir, Long.toString(lastdeploymentDate));
                processClassLoader = createProcessClassloader(processDefinitionID, processApplicationsResourcesDir);
            }
        } catch (final Exception e) {
            final String message = "Unable to create the class loader for the libraries of process " + processDefinitionID;
             if (LOGGER.isErrorEnabled()) {
                LOGGER.error( message, e);
            }
        }
        return processClassLoader;
    }

    private static long getLastDeploymentDate(final File processDir) {
        final File[] directories = processDir.listFiles(File::isDirectory);
        long lastDeploymentDate = 0L;
        for (final File directory : directories) {
            try {
                final long deploymentDate = Long.parseLong(directory.getName());
                if (deploymentDate > lastDeploymentDate) {
                    lastDeploymentDate = deploymentDate;
                }
            } catch (final Exception e) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(
                            "Process application resources deployment folder contains a directory that does not match a process deployment timestamp: "
                                    + directory.getName(), e);
                }
            }
        }
        if (lastDeploymentDate == 0L) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn(
                        "Process application resources deployment folder contains no directory that match a process deployment timestamp.");
            }
        }
        return lastDeploymentDate;
    }

    /**
     * Delete the the web resources directory if it exists
     *
     * @param session
     *            the API session
     * @param processDefinitionID
     *            the process definition ID
     */
    public static synchronized void removeApplicationFiles(final APISession session, final long processDefinitionID) {

        PROCESS_CLASSLOADERS.remove(processDefinitionID);
        try {
            final ProcessAPI processAPI = bpmEngineAPIUtil.getProcessAPI(session);
            final ProcessDefinition processDefinition = processAPI.getProcessDefinition(processDefinitionID);
            final String processUUID = processDefinition.getName() + UUID_SEPARATOR + processDefinition.getVersion();
            final File formsDir = new File(WebBonitaConstantsUtils.getTenantInstance().getFormsWorkFolder(), processUUID);
            final boolean deleted = deleteDirectory(formsDir);
            if (!deleted) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn( "unable to delete the web resources directory " + formsDir.getCanonicalPath()
                            + ". You will be able to delete it manually once the JVM will shutdown");
                }
            }
        } catch (final Exception e) {
             if (LOGGER.isErrorEnabled()) {
                LOGGER.error( "Error while deleting the web resources directory for process " + processDefinitionID, e);
            }
        }
    }

    /**
     * Get the process resource directory
     */
    public static File getApplicationResourceDir(final APISession session, final long processDefinitionID, final Date processDeploymentDate)
            throws InvalidSessionException, ProcessDefinitionNotFoundException, RetrieveException, BPMEngineException {
        final ProcessAccessor process = new ProcessAccessor(bpmEngineAPIUtil.getProcessAPI(session));
        final ProcessDefinition processDefinition = process.getDefinition(processDefinitionID);
        final String processUUID = processDefinition.getName() + UUID_SEPARATOR + processDefinition.getVersion();
        return new File(WebBonitaConstantsUtils.getTenantInstance().getFormsWorkFolder(), processUUID + File.separator
                + processDeploymentDate.getTime());
    }

    /**
     * Delete a directory and its content
     *
     * @param directory
     *            the directory to delete
     * @return return true if the directory and its content were deleted successfully, false otherwise
     */
    private static boolean deleteDirectory(final File directory) {
        boolean success = true;;
        if (directory.exists()) {
            final File[] files = directory.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    success &= deleteDirectory(files[i]);
                } else {
                    success &= files[i].delete();
                }
            }
            success &= directory.delete();
        }
        return success;
    }

    protected static File getCurrentBDMFolder(final APISession session) {
        File bdmWorkDir = null;
        final String businessDataModelVersion = getBusinessDataModelVersion(session);
        if (businessDataModelVersion != null) {
            bdmWorkDir = new File(WebBonitaConstantsUtils.getTenantInstance().geBDMWorkFolder(),
                    businessDataModelVersion);
        }
        return bdmWorkDir;
    }

    protected static void cleanBDMFolder(final File currentBDMFolder) {
        if (currentBDMFolder != null) {
            final File parentFile = currentBDMFolder.getParentFile();
            if (parentFile != null && parentFile.exists()) {
                final File[] listFiles = currentBDMFolder.getParentFile().listFiles();
                if (listFiles != null) {
                    for (final File previousDeployedBDM : listFiles) {
                        if (previousDeployedBDM.isDirectory()) {
                            try {
                                FileUtils.deleteDirectory(previousDeployedBDM);
                            } catch (final IOException e) {
                                final String message = "Unable to delete obsolete bdm libraries";
                                if (LOGGER.isWarnEnabled()) {
                                    LOGGER.warn( message, e);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected static void updateBDMClientFolder(final APISession session, final File bdmWorkDir) {
        if (!bdmWorkDir.exists()) {
            bdmWorkDir.mkdirs();
        }
        try {
            final TenantAdministrationAPI tenantAdministrationAPI = TenantAPIAccessor.getTenantAdministrationAPI(session);
            final byte[] clientBDMZip = tenantAdministrationAPI.getClientBDMZip();
            unzipContentToFolder(clientBDMZip, bdmWorkDir);
        } catch (final BonitaHomeNotSetException | UnknownAPITypeException | IOException | ServerAPIException e) {
            final String message = "Unable to create the class loader for the bdm libraries";
             if (LOGGER.isErrorEnabled()) {
                LOGGER.error( message, e);
            }
        } catch (final BusinessDataRepositoryException e) {
            final String message = "Unable to create the class loader for the bdm libraries, maybe no bdm has been installed";
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(message, e);
            }
        }
    }

    private static void unzipContentToFolder(final byte[] zipContent, final File targetFolder) throws IOException {
        ByteArrayInputStream is = null;
        ZipInputStream zis = null;
        FileOutputStream out = null;
        try {
            is = new ByteArrayInputStream(zipContent);
            zis = new ZipInputStream(is);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                final String entryName = entry.getName();
                if (entryName.endsWith(".jar")) {
                    final File file = new File(targetFolder, entryName);
                    if (file.exists()) {
                        file.delete();
                    }
                    file.createNewFile();
                    out = new FileOutputStream(file);
                    int len;
                    final byte[] buffer = new byte[1024];
                    while ((len = zis.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    out.close();
                }
            }
        } finally {
            if (is != null) {
                is.close();
            }
            if (zis != null) {
                zis.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    public static String getBusinessDataModelVersion(final APISession session) {
        try {
            final TenantAdministrationAPI tenantAdministrationAPI = TenantAPIAccessor.getTenantAdministrationAPI(session);
            return tenantAdministrationAPI.getBusinessDataModelVersion();
        } catch (final Exception e) {
            final String message = "Unable to retrieve business data model version";
             if (LOGGER.isErrorEnabled()) {
                LOGGER.error( message, e);
            }
            return null;
        }
    }

    /**
     * Create a classloader for the process
     *
     * @param processDefinitionID
     *            the process definition ID
     * @param bdmFolder
     *            the process application resources directory
     * @return a Classloader
     */
    protected static ClassLoader createProcessClassloaderWithBDM(final long processDefinitionID, final File bdmFolder, final ClassLoader parentClassloader)
            throws IOException {
        ClassLoader processClassLoader = null;
        try {
            final URL[] librariesURLs = getBDMLibrariesURLs(bdmFolder);
            if (librariesURLs.length > 0) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Creating the classloader for process " + processDefinitionID);
                }
                if (parentClassloader == null) {
                    processClassLoader = new URLClassLoader(librariesURLs, Thread.currentThread().getContextClassLoader());
                } else {
                    processClassLoader = new URLClassLoader(librariesURLs, parentClassloader);
                }
            }
        } catch (final IOException e) {
            final String message = "Unable to create the class loader for the application's libraries";
             if (LOGGER.isErrorEnabled()) {
                LOGGER.error( message, e);
            }
            throw new IOException(message);
        }
        return processClassLoader;
    }

    protected static URL[] getBDMLibrariesURLs(final File bdmFolder) throws IOException {
        final List<URL> urls = new ArrayList<>();
        if (bdmFolder.exists()) {
            final File[] bdmFiles = bdmFolder.listFiles((arg0, arg1) -> arg1.endsWith(".jar"));
            if (bdmFiles != null) {
                for (File bdmFile : bdmFiles) {
                    urls.add(bdmFile.toURI().toURL());
                }
            }
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("The bdm directory doesn't exists.");
            }
        }
        final URL[] urlArray = new URL[urls.size()];
        urls.toArray(urlArray);
        return urlArray;
    }

    protected static synchronized void createAndSaveProcessClassloader(final APISession session, final long processDefinitionID,
                                                                       final File currentBDMFolder) {

        final ClassLoader parentClassLoader = createProcessClassloader(session, processDefinitionID);
        ClassLoader processClassLoader = null;
        try {
            if (currentBDMFolder != null) {
                if (!currentBDMFolder.exists() || currentBDMFolder.listFiles().length == 0) {
                    updateBDMClientFolder(session, currentBDMFolder);
                }
                processClassLoader = createProcessClassloaderWithBDM(processDefinitionID, currentBDMFolder, parentClassLoader);
            }
            processClassLoader = setCorrectHierarchicalClassLoader(processClassLoader, parentClassLoader);
        } catch (final IOException e) {
            final String message = "Unable to create the class loader for the application's libraries";
             if (LOGGER.isErrorEnabled()) {
                LOGGER.error( message, e);
            }
        }
        PROCESS_CLASSLOADERS.put(processDefinitionID, processClassLoader);
    }
}
