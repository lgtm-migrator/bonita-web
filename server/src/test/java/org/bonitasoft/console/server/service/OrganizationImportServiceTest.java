package org.bonitasoft.console.server.service;

import org.bonitasoft.console.common.server.i18n.I18n;
import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.identity.ImportPolicy;
import org.bonitasoft.engine.identity.InvalidOrganizationFileFormatException;
import org.bonitasoft.engine.session.InvalidSessionException;
import org.bonitasoft.web.toolkit.server.ServiceException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletResponse;
import java.io.File;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Julien Mege
 */
@RunWith(MockitoJUnitRunner.class)
public class OrganizationImportServiceTest {
    
    @Mock
    IdentityAPI identityAPI;
    @Mock
    HttpServletResponse httpServletResponse;
    private OrganizationImportService organizationImportService;

    @BeforeClass
    public static void setup() {
        I18n.getInstance();
    }

    @Before
    public void before() throws Exception {
        organizationImportService = spy(new OrganizationImportService());
        doReturn(httpServletResponse).when(organizationImportService).getHttpResponse();
        doReturn(identityAPI).when(organizationImportService).getIdentityAPI();
    }

    @Test
    public void should_verify_authorisation_for_the_given_location_param() {
        doReturn(".." + File.separator + ".." + File.separator + ".." + File.separator + "file.txt").when(organizationImportService).getFileUploadParameter();

        try {
            organizationImportService.run();
        } catch (final ServiceException e) {
            assertTrue(e.getCause().getMessage().startsWith("Unauthorized access to the file"));
        }
    }
    
    @Test(expected=ServiceException.class)
    public void should_genrate_401_when_session_expires() throws Exception {
        doReturn(new byte[0]).when(organizationImportService).getOrganizationContent(any());
        doReturn("MERGE_DUPLICATES").when(organizationImportService).getParameter(anyString());

        doThrow(new InvalidSessionException("session expired")).when(identityAPI).importOrganizationWithWarnings(anyString(), any());

        try {
            organizationImportService.run();
        } finally {
            verify(httpServletResponse).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    @Test(expected=ServiceException.class)
    public void should_genrate_400_when_file_is_invalid() throws Exception {
        doReturn(new byte[0]).when(organizationImportService).getOrganizationContent(any());
        doReturn("MERGE_DUPLICATES").when(organizationImportService).getParameter(anyString());
        doThrow(new InvalidOrganizationFileFormatException("invalid format")).when(identityAPI).importOrganizationWithWarnings(anyString(), any());

        try {
            organizationImportService.run();
        } finally {
            verify(httpServletResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Test
    public void should_import_organization_with_specified_policy() throws Exception {
        doReturn("organizationXmlContent".getBytes()).when(organizationImportService).getOrganizationContent(any());
        doReturn("IGNORE_DUPLICATES").when(organizationImportService).getParameter("importPolicy");

        organizationImportService.run();

        verify(identityAPI).importOrganizationWithWarnings("organizationXmlContent", ImportPolicy.IGNORE_DUPLICATES);
    }

    @Test
    public void should_import_organization_with_default_policy() throws Exception {
        doReturn("organizationXmlContent".getBytes()).when(organizationImportService).getOrganizationContent(any());
        doReturn(null).when(organizationImportService).getParameter("importPolicy");

        organizationImportService.run();

        verify(identityAPI).importOrganizationWithWarnings("organizationXmlContent", ImportPolicy.MERGE_DUPLICATES);
    }

    @Test(expected=ServiceException.class)
    public void should_throw_an_error_when_provided_policy_is_not_valid() throws Exception {
        doReturn("organizationXmlContent".getBytes()).when(organizationImportService).getOrganizationContent(any());
        doReturn("INVALID").when(organizationImportService).getParameter("importPolicy");

        try {
            organizationImportService.run();
        } finally {
            verify(httpServletResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
