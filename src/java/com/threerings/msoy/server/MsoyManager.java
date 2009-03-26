//
// $Id$

package com.threerings.msoy.server;

import java.util.List;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.samskivert.jdbc.WriteOnlyUnit;
import com.samskivert.util.Invoker;
import com.samskivert.util.StringUtil;

import com.threerings.presents.annotation.EventThread;
import com.threerings.presents.annotation.MainInvoker;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationManager;
import com.threerings.presents.util.PersistingUnit;

import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.data.MsoyCodes;
import com.threerings.msoy.data.all.VisitorInfo;
import com.threerings.msoy.server.MemberLocal;
import com.threerings.msoy.server.MemberLogic;
import com.threerings.msoy.server.MsoyEventLogger;
import com.threerings.msoy.server.ServerConfig;
import com.threerings.msoy.server.util.MailSender;

import com.threerings.msoy.badge.data.BadgeType;
import com.threerings.msoy.badge.data.all.EarnedBadge;
import com.threerings.msoy.notify.server.NotificationManager;

import static com.threerings.msoy.Log.log;

/**
 * Handles global runtime services (that are not explicitly member related and thus not handled by
 * MemberManager}.
 */
@Singleton @EventThread
public class MsoyManager
    implements MsoyProvider
{
    @Inject public MsoyManager (InvocationManager invmgr)
    {
        // register our bootstrap invocation service
        invmgr.registerDispatcher(new MsoyDispatcher(this), MsoyCodes.MSOY_GROUP);
    }

    // from interface MemberProvider
    public void setHearingGroupChat (
        ClientObject caller, int groupId, boolean hear, InvocationService.ConfirmListener listener)
        throws InvocationException
    {
        caller.getLocal(MemberLocal.class).setHearingGroupChat(groupId, hear);
        listener.requestProcessed();
    }

    // from interface MemberProvider
    public void emailShare (ClientObject caller, boolean isGame, String placeName, int placeId,
                            String[] emails, String message, InvocationService.ConfirmListener cl)
    {
        final MemberObject memObj = (MemberObject) caller;
        String page;
        if (isGame) {
            page = "world-game_g_" + placeId;
        } else {
            page = "world-s" + placeId;
        }

        // set them up with the affiliate info
        String url = ServerConfig.getServerURL() + "welcome/" + memObj.getMemberId() +
            "/" + StringUtil.encode(page);

        final String template = isGame ? "shareGameInvite" : "shareRoomInvite";
        // username is their authentication username which is their email address
        final String from = memObj.username.toString();
        for (final String recip : emails) {
            // this just passes the buck to an executor, so we can call it from the dobj thread
            _mailer.sendTemplateEmail(
                MailSender.By.HUMAN, recip, from, template, "inviter", memObj.memberName,
                "name", placeName, "message", message, "link", url);
        }

        cl.requestProcessed();
    }

    // from interface MemberProvider
    public void trackVectorAssociation (ClientObject caller, final String vector)
    {
        final VisitorInfo info = ((MemberObject)caller).visitorInfo;
        _invoker.postUnit(new WriteOnlyUnit("trackVectorAssociation") {
            @Override public void invokePersist () throws Exception {
                _memberLogic.trackVectorAssociation(info, vector);
            }
        });
    }

    // from interface MemberProvider
    public void getABTestGroup (final ClientObject caller, final String testName,
        final boolean logEvent, final InvocationService.ResultListener listener)
    {
        final MemberObject memObj = (MemberObject) caller;
        _invoker.postUnit(new PersistingUnit("getABTestGroup", listener) {
            @Override public void invokePersistent () throws Exception {
                _testGroup = _memberLogic.getABTestGroup(testName, memObj.visitorInfo, logEvent);
            }
            @Override public void handleSuccess () {
                reportRequestProcessed(_testGroup);
            }
            protected Integer _testGroup;
        });
    }

    // from interface MemberProvider
    public void trackClientAction (final ClientObject caller, final String actionName,
        final String details)
    {
        final MemberObject memObj = (MemberObject) caller;
        if (memObj.visitorInfo == null) {
            log.warning("Failed to log client action with null visitorInfo", "caller", caller.who(),
                        "actionName", actionName);
            return;
        }
        _eventLog.clientAction(memObj.getVisitorId(), actionName, details);
    }

    // from interface MemberProvider
    public void trackTestAction (final ClientObject caller, final String actionName,
        final String testName)
    {
        final MemberObject memObj = (MemberObject) caller;
        if (memObj.visitorInfo == null) {
            log.warning("Failed to log test action with null visitorInfo", "caller", caller.who(),
                        "actionName", actionName);
            return;
        }

        _invoker.postUnit(new Invoker.Unit("getABTestGroup") {
            @Override public boolean invoke () {
                int abTestGroup = -1;
                String actualTestName;
                if (testName != null) {
                    // grab the group without logging a tracking event about it
                    abTestGroup = _memberLogic.getABTestGroup(testName, memObj.visitorInfo, false);
                    actualTestName = testName;
                } else {
                    actualTestName = "";
                }
                _eventLog.testAction(memObj.getVisitorId(), actionName, actualTestName,
                    abTestGroup);
                return false;
            }
        });
    }

    // from interface MemberProvider
    public void loadAllBadges (ClientObject caller, InvocationService.ResultListener listener)
        throws InvocationException
    {
        long now = System.currentTimeMillis();
        List<EarnedBadge> badges = Lists.newArrayList();
        for (BadgeType type : BadgeType.values()) {
            int code = type.getCode();
            for (int ii = 0; ii < type.getNumLevels(); ii++) {
                String levelUnits = type.getRequiredUnitsString(ii);
                int coinValue = type.getCoinValue(ii);
                badges.add(new EarnedBadge(code, ii, levelUnits, coinValue, now));
            }
        }

        listener.requestProcessed(badges.toArray(new EarnedBadge[badges.size()]));
    }

    // from interface MemberProvider
    public void dispatchDeferredNotifications (ClientObject caller)
    {
        _notifyMan.dispatchDeferredNotifications((MemberObject)caller);
    }

    // dependencies
    @Inject protected @MainInvoker Invoker _invoker;
    @Inject protected MailSender _mailer;
    @Inject protected MemberLogic _memberLogic;
    @Inject protected MsoyEventLogger _eventLog;
    @Inject protected NotificationManager _notifyMan;
}
