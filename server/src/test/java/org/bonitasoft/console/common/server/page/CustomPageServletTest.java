package org.bonitasoft.console.common.server.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;

import org.bonitasoft.console.common.server.utils.TenantFolder;
import org.bonitasoft.engine.session.APISession;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class CustomPageServletTest {

    MockHttpServletRequest hsRequest = new MockHttpServletRequest();

    MockHttpServletResponse hsResponse = new MockHttpServletResponse();

    @Mock
    HttpSession httpSession;

    @Mock
    APISession apiSession;

    @Mock
    CustomPageAuthorizationsHelper customPageAuthorizationsHelper;

    @Mock
    PageRenderer pageRenderer;

    @Mock
    ResourceRenderer resourceRenderer;

    @Mock
    PageResourceProvider pageResourceProvider;

    @Mock
    TenantFolder tenantFolder;

    @Spy
    @InjectMocks
    CustomPageServlet servlet;


    @Before
    public void beforeEach() throws Exception {
        hsRequest.setSession(httpSession);
        doReturn(apiSession).when(httpSession).getAttribute("apiSession");
        doReturn(1L).when(apiSession).getTenantId();
        doReturn(customPageAuthorizationsHelper).when(servlet).getCustomPageAuthorizationsHelper(apiSession);
    }

    @Test
    public void should_get_Forbidden_Status_when_page_unAuthorize() throws Exception {
        hsRequest.setPathInfo("/pageToken/");
        hsRequest.setParameter("applicationId", "1");
        given(resourceRenderer.getPathSegments(hsRequest)).willReturn(Arrays.asList("pageToken"));
        given(customPageAuthorizationsHelper.isPageAuthorized("1", "pageToken")).willReturn(false);

        servlet.doGet(hsRequest, hsResponse);

        assertThat(hsResponse.getStatus()).isEqualTo(403);
        assertThat(hsResponse.getErrorMessage()).isEqualTo("User not Authorized");
    }

    @Test
    public void should_get_badRequest_Status_when_page_name_is_not_set() throws Exception {
        hsRequest.setPathInfo("/");

        servlet.doGet(hsRequest, hsResponse);

        assertThat(hsResponse.getStatus()).isEqualTo(400);
        assertThat(hsResponse.getErrorMessage()).isEqualTo("The name of the page is required.");
    }

    @Test
    public void should_redirect_to_valide_url_on_missing_slash() throws Exception {
        hsRequest.setRequestURI("/bonita/portal/custom-page/custompage_htmlexample");
        hsRequest.setPathInfo("/custompage_htmlexample");

        servlet.doGet(hsRequest, hsResponse);

        assertThat(hsResponse.getRedirectedUrl()).endsWith("/custompage_htmlexample/");
    }

    @Test
    public void getPage_should_call_the_page_renderer() throws Exception {
        testPageIsWellCalled("custompage_htmlexample1", "/custompage_htmlexample1/", Arrays.asList("custompage_htmlexample1"));
        testPageIsWellCalled("custompage_htmlexample2", "/custompage_htmlexample2/index", Arrays.asList("custompage_htmlexample2","index"));
        testPageIsWellCalled("custompage_htmlexample3", "/custompage_htmlexample3/Index", Arrays.asList("custompage_htmlexample3","Index"));
        testPageIsWellCalled("custompage_htmlexample4", "/custompage_htmlexample4/index.html", Arrays.asList("custompage_htmlexample4","index.html"));
        testPageIsWellCalled("custompage_htmlexample5", "/custompage_htmlexample5/index.groovy", Arrays.asList("custompage_htmlexample5","index.groovy"));
    }

    private void testPageIsWellCalled(String token, String path, List<String> pathSegment) throws Exception {
        hsRequest.setPathInfo(path);
        given(resourceRenderer.getPathSegments(hsRequest)).willReturn(pathSegment);
        given(customPageAuthorizationsHelper.isPageAuthorized(null, token)).willReturn(true);

        servlet.doGet(hsRequest, hsResponse);

        verify(pageRenderer, times(1)).displayCustomPage(hsRequest, hsResponse, apiSession, token);
    }

    @Test
    public void getResource_should_call_the_resource_renderer() throws Exception {
        hsRequest.setPathInfo("/custompage_htmlexample/css/file.css");
        File pageDir = new File("/pageDir");
        given(resourceRenderer.getPathSegments(hsRequest)).willReturn(Arrays.asList("custompage_htmlexample", "css", "file.css"));
        doReturn(pageResourceProvider).when(pageRenderer).getPageResourceProvider("custompage_htmlexample",1L);
        doReturn(pageDir).when(pageResourceProvider).getPageDirectory();
        doReturn(true).when(tenantFolder).isInFolder(any(File.class), any(File.class));

        servlet.doGet(hsRequest, hsResponse);

        verify(resourceRenderer, times(1)).renderFile(hsRequest, hsResponse, new File(pageDir, File.separator+"resources"+File.separator+"css"+File.separator+"file.css"));
    }

    @Test(expected=ServletException.class)
    public void getResource_should_throw_exception_if_unauthorised() throws Exception {
        hsRequest.setPathInfo("/custompage_htmlexample/css/../../../file.css");
        File pageDir = new File(".");
        given(resourceRenderer.getPathSegments(hsRequest)).willReturn(Arrays.asList("custompage_htmlexample", "css", "..", "..", "..", "file.css"));
        doReturn(pageResourceProvider).when(pageRenderer).getPageResourceProvider("custompage_htmlexample", 1L);
        given(pageResourceProvider.getPageDirectory()).willReturn(pageDir);
        doReturn(false).when(tenantFolder).isInFolder(any(File.class), any(File.class));

        servlet.doGet(hsRequest, hsResponse);
    }

}
