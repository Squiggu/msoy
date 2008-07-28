//
// $Id$

package client.msgs;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.ServiceDefTarget;

import com.threerings.msoy.group.gwt.GroupService;
import com.threerings.msoy.group.gwt.GroupServiceAsync;
import com.threerings.msoy.person.gwt.ProfileService;
import com.threerings.msoy.person.gwt.ProfileServiceAsync;

import client.shell.Page;

/**
 * Configures our {@link CMsgs} for msgs-derived pages.
 */
public abstract class MsgsEntryPoint extends Page
{
    @Override // from Page
    protected void initContext ()
    {
        super.initContext();

        // wire up our remote services
        CMsgs.profilesvc = (ProfileServiceAsync)GWT.create(ProfileService.class);
        ((ServiceDefTarget)CMsgs.profilesvc).setServiceEntryPoint("/profilesvc");
        CMsgs.groupsvc = (GroupServiceAsync)GWT.create(GroupService.class);
        ((ServiceDefTarget)CMsgs.groupsvc).setServiceEntryPoint("/groupsvc");

        // load up our translation dictionaries
        CMsgs.mmsgs = (MsgsMessages)GWT.create(MsgsMessages.class);
    }
}
