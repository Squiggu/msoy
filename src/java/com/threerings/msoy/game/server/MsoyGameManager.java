//
// $Id$

package com.threerings.msoy.game.server;

import com.samskivert.util.StringUtil;

import com.threerings.parlor.game.data.GameConfig;
import com.threerings.presents.annotation.EventThread;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.server.InvocationException;

import com.threerings.crowd.server.PlaceManagerDelegate;

import com.whirled.game.server.WhirledGameManager;

import com.threerings.msoy.bureau.server.MsoyBureauClient;
import com.threerings.msoy.game.data.MsoyGameConfig;

import static com.threerings.msoy.Log.log;

/**
 * Manages a MetaSOY game.
 */
@EventThread
public class MsoyGameManager extends WhirledGameManager
{
    public MsoyGameManager ()
    {
        super();
    }

    // from interface PrizeProvider
    public void awardTrophy (ClientObject caller, String ident, int playerId,
                             InvocationService.InvocationListener listener)
        throws InvocationException
    {
        _trophyDelegate.awardTrophy(caller, ident, playerId, listener);
    }

    // from interface PrizeProvider
    public void awardPrize (ClientObject caller, String ident, int playerId,
                            InvocationService.InvocationListener listener)
        throws InvocationException
    {
        _trophyDelegate.awardPrize(caller, ident, playerId, listener);
    }

    // from interface WhirledGameProvider
    public void endGameWithScores (
        ClientObject caller, int[] playerOids, int[] scores, int payoutType, int gameMode,
        InvocationService.InvocationListener listener)
        throws InvocationException
    {
    	_awardDelegate.endGameWithScores(caller, playerOids, scores, payoutType, gameMode, listener);
    }

    // from interface WhirledGameProvider
    public void endGameWithWinners (
        ClientObject caller, int[] winnerOids, int[] loserOids, int payoutType,
        InvocationService.InvocationListener listener)
        throws InvocationException
    {
        _awardDelegate.endGameWithWinners(caller, winnerOids, loserOids, payoutType, listener);
    }

    /**
     * Returns true if the game is multiplayer, which is true if the game is not a SEATED_GAME
     * (fixed table size, player count established at game start) with exactly one player.
     */
    public boolean isMultiplayer ()
    {
        return (_gameconfig.getMatchType() != GameConfig.SEATED_GAME) || (getPlayerCount() > 1);
    }

    @Override
    public void addDelegate (PlaceManagerDelegate delegate)
    {
        super.addDelegate(delegate);

        if (delegate instanceof AwardDelegate) {
            _awardDelegate = (AwardDelegate) delegate;
        } else if (delegate instanceof AgentTraceDelegate) {
            _traceDelegate = (AgentTraceDelegate) delegate;
        } else if (delegate instanceof TrophyDelegate) {
            _trophyDelegate = (TrophyDelegate) delegate;
        }
    }

    @Override // from WhirledGameManager
    public void agentReady (ClientObject caller)
    {
        super.agentReady(caller);
        
        MsoyBureauClient client = (MsoyBureauClient)_bureauReg.lookupClient(getBureauId());
        if (client == null) {
            log.warning("Agent ready but no bureau client?", "gameMgr", this);
        } else {
            client.agentAdded();
            _agentAdded = true;
        }
    }

    @Override // from WhirledGameManager
    public void agentTrace (ClientObject caller, String[] trace)
    {
        super.agentTrace(caller, trace);
        _traceDelegate.recordAgentTrace(trace);
    }

    @Override // from PlaceManager
    protected void didShutdown ()
    {
        super.didShutdown();

        if (_agentAdded) {
            MsoyBureauClient client = (MsoyBureauClient)_bureauReg.lookupClient(getBureauId());
            if (client != null) {
                client.agentRemoved();
            }
        }
    }

    @Override // from PlaceManager
    public String where ()
    {
        if (_config == null || _plobj == null) {
            return super.where();
        }
        MsoyGameConfig cfg = (MsoyGameConfig)_config;
        return "[" + cfg.game.name + ":" + cfg.getGameId() + ":" + _gameobj.getOid() +
            "(" + StringUtil.toString(_gameobj.players) + ")";
    }

    @Override // from GameManager
    protected long getNoShowTime ()
    {
        // because in Whirled we start the game before the client begins downloading the game
        // media, we have to be much more lenient about noshow timing (or revamp a whole bunch of
        // other shit which maybe we'll do later)
        return 1000L * ((getPlayerSlots() == 1) ? 180 : 90);
    }

    /** A delegate that takes care of awarding flow and ratings. */
    protected AwardDelegate _awardDelegate;

    /** A delegate that takes care of awarding trophies and prizes.. */
    protected TrophyDelegate _trophyDelegate;

    /** A delegate that handles agent traces.. */
    protected AgentTraceDelegate _traceDelegate;
    
    /** Tracks whether we added an agent to our parent session. */
    protected boolean _agentAdded;
}
