//
// $Id$

package client.account;

import java.util.Date;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusListener;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.HasAlignment;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.SourcesFocusEvents;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.EnterClickAdapter;
import com.threerings.gwt.ui.SmartTable;
import com.threerings.gwt.ui.WidgetUtil;

import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.MediaDesc;
import com.threerings.msoy.person.data.Profile;
import com.threerings.msoy.web.client.DeploymentConfig;
import com.threerings.msoy.web.data.AccountInfo;
import com.threerings.msoy.web.data.SessionData;

import client.people.SendInvitesPanel;
import client.shell.Application;
import client.shell.Page;
import client.util.DateFields;
import client.util.MediaUploader;
import client.util.MsoyCallback;
import client.util.MsoyUI;
import client.util.RoundBox;
import client.util.ThumbBox;

/**
 * Displays an interface for creating a new account.
 */
public class CreateAccountPanel extends VerticalPanel
{
    public CreateAccountPanel ()
    {
        setStyleName("createAccount");
        setSpacing(10);

        add(MsoyUI.createLabel(CAccount.msgs.createIntro(), "Intro"));

        // create the account information section
        RoundBox box = new RoundBox(RoundBox.DARK_BLUE);
        box.add(new LabeledBox(CAccount.msgs.createEmail(),
                               _email = MsoyUI.createTextBox("", -1, 30),
                               CAccount.msgs.createEmailTip()));
        _email.addKeyboardListener(new EnterClickAdapter(new ClickListener() {
            public void onClick (Widget sender) {
                _password.setFocus(true);
            }
        }));
        if (Application.activeInvite != null &&
            Application.activeInvite.inviteeEmail.matches(SendInvitesPanel.EMAIL_REGEX)) {
            // provide the invitation email as the default
            _email.setText(Application.activeInvite.inviteeEmail);
        }
        _email.setFocus(true);

        box.add(WidgetUtil.makeShim(10, 10));
        box.add(new LabeledBox(CAccount.msgs.createPassword(), _password = new PasswordTextBox(),
                               CAccount.msgs.createPasswordTip(),
                               CAccount.msgs.createConfirm(), _confirm = new PasswordTextBox(),
                               CAccount.msgs.createConfirmTip()));
        _password.addKeyboardListener(new EnterClickAdapter(new ClickListener() {
            public void onClick (Widget sender) {
                _confirm.setFocus(true);
            }
        }));
        _confirm.addKeyboardListener(new EnterClickAdapter(new ClickListener() {
            public void onClick (Widget sender) {
                _name.setFocus(true);
            }
        }));
        add(makeStep(1, "What you need to log in:", box));

        // create the real you section
        box = new RoundBox(RoundBox.DARK_BLUE);
        box.add(new LabeledBox(CAccount.msgs.createRealName(),
                               _rname = MsoyUI.createTextBox("", -1, 30),
                               CAccount.msgs.createRealNameTip()));

        box.add(WidgetUtil.makeShim(10, 10));
        box.add(new LabeledBox(CAccount.msgs.createDateOfBirth(), _dateOfBirth = new DateFields(),
                               CAccount.msgs.createDateOfBirthTip()));
        add(makeStep(2, "About the real you:", box));

        // create the Whirled you section
        box = new RoundBox(RoundBox.DARK_BLUE);
        _name = MsoyUI.createTextBox("", Profile.MAX_DISPLAY_NAME_LENGTH, 30);
        box.add(new LabeledBox(CAccount.msgs.createDisplayName(), _name,
                               CAccount.msgs.createDisplayNameTip()));
        box.add(WidgetUtil.makeShim(10, 10));
        box.add(new LabeledBox(CAccount.msgs.createPhoto(), new PhotoUploader(),
                               CAccount.msgs.createPhotoTip()));
        add(makeStep(3, "About the Whirled you:", box));

        add(_status = MsoyUI.createLabel("", "Status"));

        setHorizontalAlignment(HasAlignment.ALIGN_CENTER);
        add(MsoyUI.createButton(MsoyUI.LONG_THICK, CAccount.msgs.createGo(), new ClickListener() {
            public void onClick (Widget sender) {
                createAccount();
            }
        }));

        Label slurp;
        add(slurp = new Label(""));
        setCellHeight(slurp, "100%");

        validateData(false);
    }

    // @Override // from Widget
    protected void onLoad ()
    {
        super.onLoad();
        if (_email != null) {
            _email.setFocus(true);
        }
    }

    protected boolean validateData (boolean forceError)
    {
        String email = _email.getText().trim(), name = _name.getText().trim();
        String password = _password.getText().trim(), confirm = _confirm.getText().trim();
        String status;
        FocusWidget toFocus = null;
        if (email.length() == 0) {
            status = CAccount.msgs.createMissingEmail();
            toFocus = _email;
        } else if (password.length() == 0) {
            status = CAccount.msgs.createMissingPassword();
            toFocus = _password;
        } else if (confirm.length() == 0) {
            status = CAccount.msgs.createMissingConfirm();
            toFocus = _confirm;
        } else if (!password.equals(confirm)) {
            status = CAccount.msgs.createPasswordMismatch();
            toFocus = _confirm;
        } else if (_dateOfBirth.getDate() == null) {
            status = CAccount.msgs.createMissingDoB();
            if (forceError) { // this is not a FocusWidget so we have to handle it specially
                _dateOfBirth.setFocus(true);
            }
        } else if (name.length() < Profile.MIN_DISPLAY_NAME_LENGTH) {
            status = CAccount.msgs.createNameTooShort(""+Profile.MIN_DISPLAY_NAME_LENGTH);
            toFocus = _name;
        } else {
            setStatus(CAccount.msgs.createReady());
            return true;
        }

        if (forceError) {
            if (toFocus != null) {
                toFocus.setFocus(true);
            }
            setError(status);
        } else {
            setStatus(status);
        }
        return false;
    }

    protected Widget makeStep (int step, String title, Widget contents)
    {
        SmartTable table = new SmartTable("Step", 0, 0);
        table.setText(0, 0, step + ".", 1, "Number");
        table.getFlexCellFormatter().setRowSpan(0, 0, 2);
        table.setText(0, 1, title, 1, "Title");
        table.setWidget(1, 0, contents, 1, null);
        return table;
    }

    protected void createAccount ()
    {
        if (!validateData(true)) {
            return;
        }

        String[] today = new Date().toString().split(" ");
        String thirteenYearsAgo = "";
        for (int ii = 0; ii < today.length; ii++) {
            if (today[ii].matches("[0-9]{4}")) {
                int year = Integer.valueOf(today[ii]).intValue();
                today[ii] = "" + (year - 13);
            }
            thirteenYearsAgo += today[ii] + " ";
        }

        Date dob = DateFields.toDate(_dateOfBirth.getDate());
        if (new Date(thirteenYearsAgo).compareTo(dob) < 0) {
            setError(CAccount.msgs.createNotThirteen());
            return;
        }

        String email = _email.getText().trim(), name = _name.getText().trim();
        String password = _password.getText().trim();
        String inviteId = (Application.activeInvite == null) ?
            null : Application.activeInvite.inviteId;
        AccountInfo info = new AccountInfo();
        info.realName = _rname.getText().trim();

        setStatus(CAccount.msgs.creatingAccount());
        CAccount.usersvc.register(
            DeploymentConfig.version, email, CAccount.md5hex(password), name, _dateOfBirth.getDate(),
            info, 1, inviteId, Application.activeGuestId, new AsyncCallback() {
            public void onSuccess (Object result) {
                // clear our current token otherwise didLogon() will try to load it
                Application.setCurrentToken(null);
                // pass our credentials into the application
                CAccount.app.didLogon((SessionData)result);
                // then head to our me page
                Application.go(Page.ME, "");
            }
            public void onFailure (Throwable caught) {
                setError(CAccount.serverError(caught));
            }
        });
    }

    protected void setStatus (String text)
    {
        _status.removeStyleName("Error");
        _status.setText(text);
    }

    protected void setError (String text)
    {
        _status.addStyleName("Error");
        _status.setText(text);
    }

    protected class PhotoUploader extends SmartTable
    {
        public PhotoUploader ()
        {
            setWidget(0, 0, new ThumbBox(Profile.DEFAULT_PHOTO, null));
            setWidget(0, 1, new MediaUploader(Item.THUMB_MEDIA, new MediaUploader.Listener() {
                public void mediaUploaded (String name, MediaDesc desc, int width, int height) {
                    _media = desc;
                    setWidget(0, 0, new ThumbBox(_media, null));
                }
            }));
        }

        protected MediaDesc _media;
    }

    protected static class LabeledBox extends FlowPanel
    {
        public LabeledBox (String title, Widget contents, String tip)
        {
            setStyleName("Box");
            _tip = new SmartTable("Tip", 0, 0);
            add(title, contents, tip);
        }

        public LabeledBox (String title1, Widget contents1, String tip1,
                           String title2, Widget contents2, String tip2)
        {
            this(title1, contents1, tip1);
            add(WidgetUtil.makeShim(3, 3));
            add(title2, contents2, tip2);
        }

        public void add (String title, final Widget contents, final String tip)
        {
            add(MsoyUI.createLabel(title, "Label"));
            add(contents);
            if (contents instanceof SourcesFocusEvents) {
                ((SourcesFocusEvents)contents).addFocusListener(new FocusListener() {
                    public void onFocus (Widget sender) {
                        // we want contents here not sender because of DateFields
                        showTip(contents, tip);
                    }
                    public void onLostFocus (Widget sender) {
                        if (_tip.isAttached()) {
                            remove(_tip);
                        }
                    }
                });
            }
        }

        protected void showTip (Widget trigger, String tip)
        {
            if (!_tip.isAttached()) {
                DOM.setStyleAttribute(_tip.getElement(), "left",
                                      (trigger.getOffsetWidth()+15) + "px");
                DOM.setStyleAttribute(_tip.getElement(), "top",
                                      (trigger.getAbsoluteTop() - getAbsoluteTop() +
                                       trigger.getOffsetHeight()/2 - 27) + "px");
                _tip.setText(0, 0, tip);
                add(_tip);
            }
        }

        protected SmartTable _tip;
    }

    protected TextBox _email, _name, _rname;
    protected PasswordTextBox _password, _confirm;
    protected DateFields _dateOfBirth;
    protected Label _status;
}
