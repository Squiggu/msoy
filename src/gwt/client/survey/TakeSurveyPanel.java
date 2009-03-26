package client.survey;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.SmartTable;

import com.threerings.msoy.survey.gwt.Survey;
import com.threerings.msoy.survey.gwt.SurveyQuestion;
import com.threerings.msoy.survey.gwt.SurveyResponse;
import com.threerings.msoy.survey.gwt.SurveyService;
import com.threerings.msoy.survey.gwt.SurveyServiceAsync;

import client.ui.MsoyUI;
import client.util.ClickCallback;
import client.util.ServiceUtil;

public class TakeSurveyPanel extends VerticalPanel
{
    public TakeSurveyPanel (int surveyId)
    {
        _surveyId = surveyId;
        setStyleName("takeSurvey");
        setSpacing(10);
        setWidth("100%");
        _surveySvc.getSurvey(surveyId, new AsyncCallback<Survey>() {
            public void onFailure (Throwable caught) {
                add(MsoyUI.createLabel(_msgs.errSurveyNotLoaded(caught.getMessage()), "error"));
            }
            public void onSuccess (Survey result) {
                setSurvey(result);
            }
        });
    }

    protected void setSurvey (Survey survey)
    {
        _survey = survey;

        add(MsoyUI.createLabel(survey.name, "name"));

        SmartTable questions = new SmartTable(3, 0);
        questions.setWidth("100%");
        questions.setStyleName("questions");
        add(questions);

        _questions = new QuestionUI[_survey.questions.length];
        for (int ii = 0; ii < _survey.questions.length; ++ii) {
            SurveyQuestion q = _survey.questions[ii];
            questions.setText(ii * 2, 0, (ii + 1) + ".", 1, "number");
            questions.setText(ii * 2, 1, q.text, 1, "text");

            switch (q.type) {
            case BOOLEAN:
                _questions[ii] = new TrueFalse();
                break;
            case EXCLUSIVE_CHOICE:
                _questions[ii] = new ExclusiveChoice();
                break;
            case SUBSET_CHOICE:
                _questions[ii] = new SubsetChoice();
                break;
            case RATING:
                _questions[ii] = new Rating();
                break;
            case FREE_FORM:
                _questions[ii] = new Essay();
                break;
            }
            questions.setText(ii * 2 + 1, 0, "");
            if (_questions[ii] != null) {
                questions.setWidget(ii * 2 + 1, 1, _questions[ii].makeWidget(ii, q), 1, "ui");
            }
            questions.getRowFormatter().setStyleName(ii * 2, "question");
            questions.getRowFormatter().setStyleName(ii * 2 + 1, "response");
        }

        Button done = new Button(_msgs.doneLabel());
        new ClickCallback<Void>(done) {
            protected boolean callService () {
                gatherResponses();
                if (_responses.size() == 0) {
                    MsoyUI.error(_msgs.errNoResponses());
                    return false;
                }
                _surveySvc.submitResponse(_surveyId, _responses, this);
                return true;
            }
            protected boolean gotResult (Void result) {
                MsoyUI.info(_msgs.thankYou());
                return true;
            }
        };
        add(done);
    }

    protected void gatherResponses ()
    {
        _responses.clear();
        for (QuestionUI qui : _questions) {
            if (qui == null) {
                continue;
            }

            SurveyResponse resp = new SurveyResponse();
            resp.questionIndex = qui.getIndex();
            resp.response = qui.getResponse();

            if (resp.response != null) {
                _responses.add(resp);
            }
        }
    }

    protected abstract class QuestionUI
    {
        public Widget makeWidget (int idx, SurveyQuestion question)
        {
            _index = idx;
            _question = question;
            return makeWidget();
        }

        public int getIndex ()
        {
            return _index;
        }

        protected abstract Widget makeWidget ();
        protected abstract String getResponse();

        protected int _index;
        protected SurveyQuestion _question;
    }

    protected class TrueFalse extends QuestionUI
    {
        protected Widget makeWidget ()
        {
            // buttons for true and false
            trueButton = new RadioButton("Q" + _index, _msgs.trueLabel());
            falseButton = new RadioButton("Q" + _index, _msgs.falseLabel());

            // make the panel
            VerticalPanel panel = new VerticalPanel();
            panel.setStyleName("trueFalse");
            panel.add(trueButton);
            panel.add(falseButton);
            return panel;
        }

        protected String getResponse ()
        {
            if (trueButton.isChecked()) {
                return String.valueOf(true);
            }
            if (falseButton.isChecked()) {
                return String.valueOf(false);
            }
            return null;
        }

        protected RadioButton trueButton, falseButton;
    }

    protected class ExclusiveChoice extends QuestionUI
    {
        protected Widget makeWidget ()
        {
            // one button per choice
            buttons = new RadioButton[_question.choices.length];

            // create all the buttons and add them to the panel
            VerticalPanel panel = new VerticalPanel();
            for (int ii = 0; ii < _question.choices.length; ++ii) {
                panel.add(buttons[ii] = new RadioButton("Q" + _index, _question.choices[ii]));
            }
            panel.setStyleName("exclusive");
            return panel;
        }

        protected String getResponse ()
        {
            for (int ii = 0; ii < buttons.length; ++ii) {
                if (buttons[ii].isChecked()) {
                    return String.valueOf(ii);
                }
            }
            return null;
        }

        protected RadioButton[] buttons;
    }

    protected class SubsetChoice extends QuestionUI
    {
        protected Widget makeWidget ()
        {
            // one button per choice
            buttons = new CheckBox[_question.choices.length];

            // create all the buttons and add them to the panel
            VerticalPanel panel = new VerticalPanel();
            for (int ii = 0; ii < _question.choices.length; ++ii) {
                panel.add(buttons[ii] = new CheckBox(_question.choices[ii]));
            }
            panel.setStyleName("subset");
            return panel;
        }

        protected String getResponse ()
        {
            StringBuilder response = new StringBuilder();
            for (int ii = 0; ii < buttons.length; ++ii) {
                if (buttons[ii].isChecked()) {
                    if (response.length() > 0) {
                        response.append(",");
                    }
                    response.append("" + ii);
                }
            }
            if (response.length() > 0) {
                return response.toString();
            }
            return null;
        }

        protected CheckBox[] buttons;
    }

    protected class Rating extends QuestionUI
    {
        protected Widget makeWidget ()
        {
            // one button per rating value
            buttons = new RadioButton[_question.maxValue];

            // create all the buttons and add them to the panel
            HorizontalPanel panel = new HorizontalPanel();
            panel.add(MsoyUI.createLabel("1", "value"));
            for (int ii = 0; ii < _question.maxValue; ++ii) {
                panel.add(buttons[ii] = new RadioButton("Q" + _index));
            }
            panel.add(MsoyUI.createLabel("" + _question.maxValue, "value"));
            panel.setStyleName("rating");
            panel.setSpacing(10);
            return panel;
        }

        protected String getResponse ()
        {
            for (int ii = 0; ii < buttons.length; ++ii) {
                if (buttons[ii].isChecked()) {
                    return String.valueOf(ii + 1);
                }
            }
            return null;
        }

        protected RadioButton[] buttons;
    }

    protected class Essay extends QuestionUI
    {
        protected Widget makeWidget ()
        {
            return text = MsoyUI.createTextArea("", 80, 4);
        }

        protected String getResponse ()
        {
            String resp = text.getText();
            return resp.length() == 0 ? null : resp;
        }

        protected TextArea text;
    }

    public int _surveyId;
    public Survey _survey;
    public QuestionUI _questions[];
    public List<SurveyResponse> _responses = new ArrayList<SurveyResponse>();

    protected static final SurveyServiceAsync _surveySvc = (SurveyServiceAsync)(ServiceUtil.bind(
        GWT.create(SurveyService.class), SurveyService.ENTRY_POINT));
    protected static final SurveyMessages _msgs = GWT.create(SurveyMessages.class);
}