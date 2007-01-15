//
// $Id$

package com.threerings.msoy.swiftly.client;        

import java.awt.Container;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import com.samskivert.swing.HGroupLayout;
import com.samskivert.swing.VGroupLayout;

import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;

import com.threerings.crowd.client.PlacePanel;
import com.threerings.crowd.client.PlaceView;
import com.threerings.crowd.data.PlaceObject;

import com.threerings.micasa.client.ChatPanel;
import com.threerings.micasa.client.OccupantList;

import com.threerings.msoy.swiftly.data.DocumentElement;
import com.threerings.msoy.swiftly.data.PathElement;
import com.threerings.msoy.swiftly.data.ProjectRoomObject;
import com.threerings.msoy.swiftly.data.SwiftlyCodes;
import com.threerings.msoy.swiftly.util.SwiftlyContext;

public class SwiftlyEditor extends PlacePanel
    implements AttributeChangeListener
{
    public SwiftlyEditor (ProjectRoomController ctrl, SwiftlyContext ctx)
    {
        super(ctrl);
        _ctx = ctx;

        setLayout(new VGroupLayout(VGroupLayout.STRETCH, VGroupLayout.STRETCH, 5,
                                   VGroupLayout.TOP));
        // let's not jam ourselves up against the edges of the window
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // setup the components
        _tabs = new TabbedEditor(_ctx, this);
        _tabs.setMinimumSize(new Dimension(400, 0));

        _projectPanel = new ProjectPanel(_ctx, this);
        _projectPanel.setMinimumSize(new Dimension(0, 0));

        _toolbar = new EditorToolBar(ctrl, _ctx, this);
        _splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, _tabs, _projectPanel);
        // TODO apparently GTK does not have the graphic for this. What to do?
        _splitPane.setOneTouchExpandable(true);

        // layout the window
        add(_toolbar, VGroupLayout.FIXED);
        add(_splitPane);

        JPanel panel = new JPanel(
            new HGroupLayout(HGroupLayout.STRETCH, HGroupLayout.STRETCH, 5, HGroupLayout.LEFT));
        panel.setPreferredSize(new Dimension(0, 200));
        panel.add(new ChatPanel(_ctx));
        OccupantList ol;
        panel.add(ol = new OccupantList(_ctx), HGroupLayout.FIXED);
        ol.setPreferredSize(new Dimension(100, 0));
        add(panel, VGroupLayout.FIXED);

        _splitPane.setDividerLocation(0.8);
    }

    public void addEditorTab (DocumentElement document)
    {
        _tabs.addEditorTab(document);
    }

    public void updateTabTitleAt (DocumentElement document)
    {
        _tabs.updateTabTitleAt(document);
    }

    public void updateTabDocument (DocumentElement document)
    {
        _tabs.updateTabDocument(document);
    }

    public void updateCurrentTabTitle ()
    {
        _tabs.updateCurrentTabTitle();
    }

    public void closeCurrentTab ()
    {
        _tabs.closeCurrentTab();
    }

    public AbstractAction createCloseCurrentTabAction ()
    {
        return _tabs.createCloseCurrentTabAction();
    }

    public EditorToolBar getToolbar()
    {
        return _toolbar;
    }

    public ProjectPanel getProjectPanel ()
    {
        return _projectPanel;
    }

    /**
     * Shows a modal, internal frame dialog prompting the user to name a {@link FileElement}
     * @param the type of {@link FileElement} to name
     * @return true if the user picked a name, false if they clicked cancel
     */
    public String showSelectPathElementNameDialog (PathElement.Type fileElementType)
    {
        String prompt;
        prompt = _ctx.xlate(SwiftlyCodes.SWIFTLY_MSGS, "m.dialog.select_name." + fileElementType);
        return JOptionPane.showInternalInputDialog(this, prompt);
    }

    /**
     * Shows a modal, internal frame dialog reporting an error to the user.
     * @param the error message to display
     */
    public void showErrorDialog (String message)
    {
        JOptionPane.showInternalMessageDialog(this, message,
            _ctx.xlate(SwiftlyCodes.SWIFTLY_MSGS, "m.dialog.error.title"),
            JOptionPane.ERROR_MESSAGE);
    }

    @Override // from PlacePanel
    public void willEnterPlace (PlaceObject plobj)
    {
        _roomObj = (ProjectRoomObject)plobj;
        _roomObj.addListener(this);

        // let our project panel know about all the roomy goodness
        _projectPanel.setProject(_roomObj);
    }

    @Override // from PlacePanel
    public void didLeavePlace (PlaceObject plobj)
    {
        if (_roomObj != null) {
            _roomObj.removeListener(this);
            _roomObj = null;
        }

        // TODO: shutdown the project panel?
    }

    // from interface AttributeChangeListener
    public void attributeChanged (AttributeChangedEvent event)
    {
        if (event.getName().equals(ProjectRoomObject.CONSOLE)) {
            // TODO: append this to a console instead of just jamming it into a status bar
            // _statusbar.setLabel(_ctx.xlate(SwiftlyCodes.SWIFTLY_MSGS, _roomObj.console));
        }
    }

    protected void saveDocumentElement (DocumentElement doc)
    {
        System.err.println("Saving... " + doc);
        _roomObj.service.updatePathElement(_ctx.getClient(), doc);
    }

    protected SwiftlyContext _ctx;
    protected ProjectRoomObject _roomObj;

    protected TabbedEditor _tabs;
    protected EditorToolBar _toolbar;
    protected ProjectPanel _projectPanel;
    protected PathElement _project;
    protected JSplitPane _splitPane;
}
