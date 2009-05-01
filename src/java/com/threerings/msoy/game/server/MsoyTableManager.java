//
// $Id$

package com.threerings.msoy.game.server;

import com.threerings.presents.annotation.EventThread;
import com.threerings.presents.dobj.RootDObjectManager;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationManager;

import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.server.PlaceRegistry;

import com.threerings.parlor.data.Table;
import com.threerings.parlor.game.data.GameConfig;
import com.threerings.parlor.game.data.GameObject;
import com.threerings.parlor.game.server.GameManager;
import com.threerings.parlor.server.TableManager;

import com.threerings.msoy.game.data.GameSummary;
import com.threerings.msoy.game.data.LobbyObject;
import com.threerings.msoy.game.data.MsoyMatchConfig;
import com.threerings.msoy.game.data.ParlorGameConfig;
import com.threerings.msoy.game.data.PlayerObject;

/**
 * Customizes the basic table manager with MSOY specific bits.
 */
@EventThread
public class MsoyTableManager extends TableManager
{
    public MsoyTableManager (RootDObjectManager omgr, InvocationManager invmgr, PlaceRegistry plreg,
                             PlayerNodeActions playerActions, LobbyManager lmgr)
    {
        super(omgr, invmgr, plreg, lmgr.getLobbyObject());

        _playerActions = playerActions;
        _lmgr = lmgr;
        _lobj = lmgr.getLobbyObject();

        _allowBooting = true;
    }

    @Override
    protected GameConfig createConfig (Table table)
    {
        ParlorGameConfig config = (ParlorGameConfig)super.createConfig(table);
        _lmgr.initConfig(config);
        return config;
    }

    @Override
    protected void notePlayerAdded (Table table, BodyObject body)
    {
        super.notePlayerAdded(table, body);

        // mark this player as "in" this game if they're not already
        _playerActions.updatePlayerGame((PlayerObject) body, new GameSummary(_lobj.game));
    }

    @Override
    protected GameManager createGameManager (GameConfig config)
        throws InstantiationException, InvocationException
    {
        return _lmgr.createGameManager((ParlorGameConfig)config);
    }

    @Override
    protected void gameCreated (Table table, GameObject gameobj, GameManager gmgr)
    {
        super.gameCreated(table, gameobj, gmgr);

        // remove unactionable tables from the lobby as normal players don't see them and we don't
        // want huge numbers of hidden single player tables clogging up our lobby object
        ParlorGameConfig config = (ParlorGameConfig)table.config;
        MsoyMatchConfig matchConfig = (MsoyMatchConfig)config.getGameDefinition().match;
        if (config.getMatchType() != GameConfig.PARTY && matchConfig.unwatchable &&
            // we need to keep at least one table around otherwise the lobby will shutdown which is
            // bad, oh god the hackery
            _lobj.tables.size() > 1) {
            purgeTable(table);
        }
    }

    protected LobbyManager _lmgr;
    protected LobbyObject _lobj;
    protected PlayerNodeActions _playerActions;
}
