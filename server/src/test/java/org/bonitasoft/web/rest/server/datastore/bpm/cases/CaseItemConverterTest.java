package org.bonitasoft.web.rest.server.datastore.bpm.cases;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import org.bonitasoft.console.common.server.i18n.I18n;
import org.bonitasoft.engine.bpm.process.ProcessInstance;
import org.bonitasoft.web.rest.model.bpm.cases.CaseItem;
import org.bonitasoft.web.toolkit.client.common.CommonDateFormater;
import org.bonitasoft.web.toolkit.server.utils.ServerDateFormater;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CaseItemConverterTest {
    @Mock
    private ProcessInstance processInstance;

    private final CaseItemConverter caseItemConverter = new CaseItemConverter();

    @BeforeClass
    public static void beforeClass() {
        I18n.getInstance();
        CommonDateFormater.setDateFormater(new ServerDateFormater());
    }


    @Test
    public void testConvertShouldReturnAEngineCaseConvertedIntoAConsoleCase() throws Exception {
        //given
        doReturn("labelOne").when(processInstance).getStringIndexLabel(1);
        doReturn("labelTwo").when(processInstance).getStringIndexLabel(2);
        doReturn("labelThree").when(processInstance).getStringIndexLabel(3);
        doReturn("labelFour").when(processInstance).getStringIndexLabel(4);
        doReturn("labelFive").when(processInstance).getStringIndexLabel(5);
        doReturn("valueOne").when(processInstance).getStringIndex1();
        doReturn("valueTwo").when(processInstance).getStringIndex2();
        doReturn("valueThree").when(processInstance).getStringIndex3();
        doReturn("valueFour").when(processInstance).getStringIndex4();
        doReturn("valueFive").when(processInstance).getStringIndex5();

        // when
        final CaseItem caseItem = caseItemConverter.convert(processInstance);

        // then
        //check labels
        assertThat(caseItem.getSearchIndex1Label()).isEqualTo("labelOne");
        assertThat(caseItem.getSearchIndex2Label()).isEqualTo("labelTwo");
        assertThat(caseItem.getSearchIndex3Label()).isEqualTo("labelThree");
        assertThat(caseItem.getSearchIndex4Label()).isEqualTo("labelFour");
        assertThat(caseItem.getSearchIndex5Label()).isEqualTo("labelFive");
        //check values
        assertThat(caseItem.getSearchIndex1Value()).isEqualTo("valueOne");
        assertThat(caseItem.getSearchIndex2Value()).isEqualTo("valueTwo");
        assertThat(caseItem.getSearchIndex3Value()).isEqualTo("valueThree");
        assertThat(caseItem.getSearchIndex4Value()).isEqualTo("valueFour");
        assertThat(caseItem.getSearchIndex5Value()).isEqualTo("valueFive");
    }
}