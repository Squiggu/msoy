//
// $Id$

package com.threerings.msoy.notify.data;

import com.threerings.util.ActionScript;
import com.threerings.util.MessageBundle;

import com.threerings.orth.notify.data.Notification;

import com.threerings.msoy.data.all.MemberName;

/**
 * Notifies a user that an invitation was accepted
 */
@com.threerings.util.ActionScript(omit=true)
public class InviteAcceptedNotification extends Notification
{
    public InviteAcceptedNotification ()
    {
    }

    @ActionScript(omit=true)
    public InviteAcceptedNotification (MemberName invitee, String inviteeEmail)
    {
        _invitee = invitee.toMemberName();
        _inviteeEmail = inviteeEmail;
    }

    // from Notification
    public String getAnnouncement ()
    {
        return MessageBundle.tcompose(
            "m.invite_accepted", _inviteeEmail, _invitee, _invitee.getId());
    }

    @Override
    public MemberName getSender ()
    {
        return _invitee;
    }

    protected MemberName _invitee;
    protected String _inviteeEmail;
}
