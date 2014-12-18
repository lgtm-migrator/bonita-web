package org.bonitasoft.console.common.server.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;

import java.io.File;

import org.bonitasoft.console.common.server.preferences.constants.WebBonitaConstantsUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TenantFolderTest {

    @Mock
    private WebBonitaConstantsUtils webBonitaConstantsUtils;

    @Spy
    private final TenantFolder tenantFolder = new TenantFolder();

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void should_authorized_a_file_in_temp_folder() throws Exception {
        given(webBonitaConstantsUtils.getTempFolder()).willReturn(new File(".\\tempFolder"));

        final File file = new File(webBonitaConstantsUtils.getTempFolder().getAbsolutePath(), "\\..\\tempFolder\\fileName.txt");

        final boolean isInTempFolder = tenantFolder.isInTempFolder(file, webBonitaConstantsUtils);

        assertTrue(isInTempFolder);
    }

    @Test
    public void should_unauthorized_a_file_not_in_temp_folder() throws Exception {
        given(webBonitaConstantsUtils.getTempFolder()).willReturn(new File(".\\tempFolder"));

        final File file = new File(webBonitaConstantsUtils.getTempFolder().getAbsolutePath(), "\\..\\..\\..\\fileName.txt");

        final boolean isInTempFolder = tenantFolder.isInTempFolder(file, webBonitaConstantsUtils);

        assertFalse(isInTempFolder);
    }

    @Test
    public void should_authorized_a_file_in_a_specific_folder() throws Exception {

        final File folder = new File(".\\anyFolder");

        final File file = new File(".\\anyFolder\\..\\anyFolder\\fileName.txt");

        final boolean isInTempFolder = tenantFolder.isInFolder(file, folder);

        assertTrue(isInTempFolder);
    }

    @Test
    public void should_unauthorized_a_file_not_in_a_specific_folder() throws Exception {

        final File folder = new File(".\\anyFolder");

        final File file = new File(".\\anyFolder\\..\\..\\fileName.txt");

        final boolean isInTempFolder = tenantFolder.isInFolder(file, folder);

        assertFalse(isInTempFolder);
    }

    @Test
    public void should_complete_file_path() throws Exception {
        final String fileName = "fileName.txt";

        given(tenantFolder.getBonitaConstantUtil(1L)).willReturn(webBonitaConstantsUtils);
        given(webBonitaConstantsUtils.getTempFolder()).willReturn(new File("c:\\tempFolder"));

        final String completedPath = tenantFolder.getCompleteTempFilePath(fileName, 1L);

        assertThat(completedPath).isEqualTo("c:\\tempFolder\\fileName.txt");
    }

    @Test
    public void should_verifyAuthorization_file_path() throws Exception {
        final String fileName = "c:\\tempFolder\\fileName.txt";

        given(tenantFolder.getBonitaConstantUtil(1L)).willReturn(webBonitaConstantsUtils);
        given(webBonitaConstantsUtils.getTempFolder()).willReturn(new File("c:\\tempFolder"));

        final String completedPath = tenantFolder.getCompleteTempFilePath(fileName, 1L);

        assertThat(completedPath).isEqualTo("c:\\tempFolder\\fileName.txt");
    }

    @Test(expected = UnauthorizedFolderException.class)
    public void should_UnauthorizedFolder() throws Exception {
        final String fileName = "c:\\UnauthorizedFolder\\tempFolder\\fileName.txt";

        given(tenantFolder.getBonitaConstantUtil(1L)).willReturn(webBonitaConstantsUtils);
        given(webBonitaConstantsUtils.getTempFolder()).willReturn(new File("c:\\tempFolder"));

        tenantFolder.getCompleteTempFilePath(fileName, 1L);
    }

    @Test
    public void should_return_completed_temp_file() throws Exception {
        final String fileName = "fileName.txt";

        given(tenantFolder.getBonitaConstantUtil(1L)).willReturn(webBonitaConstantsUtils);
        given(webBonitaConstantsUtils.getTempFolder()).willReturn(new File("c:\\tempFolder"));

        final File completedFile = tenantFolder.getTempFile(fileName, 1L);

        assertThat(completedFile.getPath()).isEqualTo("c:\\tempFolder\\fileName.txt");
    }
}
