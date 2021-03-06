//
// $Id$

package com.threerings.msoy.game.server;

import java.sql.Timestamp;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.inject.Inject;

import com.whirled.game.client.PrizeService;
import com.whirled.game.data.GameContentOwnership;
import com.whirled.game.data.GameData;
import com.whirled.game.data.GameDataObject;
import com.whirled.game.data.ItemData;
import com.whirled.game.data.LevelData;
import com.whirled.game.data.TrophyData;

import com.samskivert.util.ResultListener;

import com.threerings.util.MessageBundle;

import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.data.InvocationCodes;
import com.threerings.presents.server.InvocationException;

import com.threerings.crowd.data.PlaceObject;

import com.threerings.parlor.server.PlayManagerDelegate;

import com.threerings.msoy.data.MsoyCodes;
import com.threerings.msoy.data.all.HashMediaDesc;
import com.threerings.msoy.game.data.MsoyGameCodes;
import com.threerings.msoy.game.data.PlayerObject;
import com.threerings.msoy.game.data.all.Trophy;
import com.threerings.msoy.game.server.persist.TrophyRecord;
import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.ItemPack;
import com.threerings.msoy.item.data.all.LevelPack;
import com.threerings.msoy.item.data.all.Prize;
import com.threerings.msoy.item.data.all.TrophySource;
import com.threerings.msoy.item.server.ItemManager;

import static com.threerings.msoy.Log.log;

/**
 * Handles awarding trophies and prizes.
 */
public class TrophyDelegate extends PlayManagerDelegate
{
    public TrophyDelegate (GameContent content)
    {
        _content = content;
    }

    /**
     * Handles {@link PrizeService#awardTrophy}.
     */
    public void awardTrophy (ClientObject caller, String ident, int playerId,
                             final InvocationService.InvocationListener listener)
        throws InvocationException
    {
        final PlayerObject plobj = (PlayerObject) verifyWritePermission(caller, playerId);

        // locate the trophy source record in question
        TrophySource source = null;
        for (TrophySource csource : _content.tsources) {
            if (csource.ident.equals(ident)) {
                source = csource;
                break;
            }
        }
        if (source == null) {
            throw new InvocationException(
                MsoyCodes.GAME_MSGS, MessageBundle.tcompose(MsoyGameCodes.E_UNKNOWN_TROPHY, ident));
        }

        // if the player already has this trophy, ignore the request
        if (plobj.ownsGameContent(_content.gameId, GameData.TROPHY_DATA, ident)) {
            log.info("Game requested to award already held trophy", "game", where(),
                     "who", plobj.who(), "ident", ident);
            return;
        }

        // add the trophy to their runtime set now to avoid repeat-call freakoutery; if we fail to
        // store the trophy to the database, we won't tell them that they earned it and they'll be
        // able to earn it again next time
        plobj.addToGameContent(
            new GameContentOwnership(_content.gameId, GameData.TROPHY_DATA, source.ident));

        // create the persistent record we will shortly store
        TrophyRecord trophy = new TrophyRecord();
        trophy.gameId = _content.gameId;
        trophy.memberId = plobj.getMemberId();
        trophy.ident = source.ident;
        trophy.name = source.name;
        trophy.trophyMediaHash = HashMediaDesc.unmakeHash(source.getThumbnailMedia());
        trophy.trophyMimeType = source.getThumbnailMedia().getMimeType();
        trophy.whenEarned = new Timestamp(System.currentTimeMillis());

        // otherwise, award them the trophy, then add it to their runtime collection
        _gameReg.awardTrophy(_content.game.name, trophy, source.description,
                             new InvocationService.ResultListener() {
            public void requestProcessed (Object result) {
                plobj.postMessage(MsoyGameCodes.TROPHY_AWARDED, (Trophy)result);
            }
            public void requestFailed (String cause) {
                listener.requestFailed(cause);
            }
        });
    }

    /**
     * Handles {@link PrizeService#awardPrize}.
     */
    public void awardPrize (ClientObject caller, String ident, int playerId,
                            final InvocationService.InvocationListener listener)
        throws InvocationException
    {
        final PlayerObject plobj = (PlayerObject) verifyWritePermission(caller, playerId);

        // locate the prize record in question
        Prize prize = null;
        for (Prize cprize : _content.prizes) {
            if (cprize.ident.equals(ident)) {
                prize = cprize;
                break;
            }
        }
        if (prize == null) {
            throw new InvocationException(
                MsoyCodes.GAME_MSGS, MessageBundle.tcompose(MsoyGameCodes.E_UNKNOWN_PRIZE, ident));
        }

        // if the player has already earned this prize during this session, ignore the request
        if (plobj.ownsGameContent(_content.gameId, GameData.PRIZE_MARKER, ident)) {
            log.info("Game requested to award already earned prize", "game", where(),
                     "who", plobj.who(), "ident", ident);
            return;
        }

        // add the prize to the runtime set now to avoid repeat-call freakoutery; if the prize
        // award fails for other wacky reasons, they'll just have to re-earn it later
        plobj.addToGameContent(
            new GameContentOwnership(_content.gameId, GameData.PRIZE_MARKER, prize.ident));

        // pass the buck to the item manager
        _itemMan.awardPrize(plobj.getMemberId(), _content.gameId, _content.game.name, prize,
                            new ResultListener<Item>() {
            public void requestCompleted (Item item) {
                plobj.postMessage(MsoyGameCodes.PRIZE_AWARDED, item);
            }
            public void requestFailed (Exception cause) {
                if (cause instanceof InvocationException) {
                    listener.requestFailed(cause.getMessage());
                } else {
                    log.warning("Prize award failed", "game", where(), cause);
                    listener.requestFailed(InvocationCodes.INTERNAL_ERROR);
                }
            }
        });
    }

    @Override
    public void didStartup (PlaceObject plobj)
    {
        super.didStartup(plobj);

        // wire up our WhirledGameService
        if (plobj instanceof GameDataObject) {
            GameDataObject gobj = (GameDataObject)plobj;

            // let the client know what game content is available
            List<GameData> gdata = Lists.newArrayList();
            for (LevelPack pack : _content.lpacks) {
                LevelData data = new LevelData();
                data.ident = pack.ident;
                data.name = pack.name;
                data.mediaURL = pack.getFurniMedia().getMediaPath();
                data.premium = pack.premium;
                gdata.add(data);
            }
            for (ItemPack pack : _content.ipacks) {
                ItemData data = new ItemData();
                data.ident = pack.ident;
                data.name = pack.name;
                data.mediaURL = pack.getFurniMedia().getMediaPath();
                gdata.add(data);
            }
            for (TrophySource source : _content.tsources) {
                TrophyData data = new TrophyData();
                data.ident = source.ident;
                data.name = source.name;
                data.mediaURL = source.getThumbnailMedia().getMediaPath();
                gdata.add(data);
            }
            gobj.setGameData(gdata.toArray(new GameData[gdata.size()]));
        }
    }

    @Override
    public void didShutdown ()
    {
        super.didShutdown();
    }

    @Override // from PlaceManagerDelegate
    public void bodyLeft (int bodyOid)
    {
        super.bodyLeft(bodyOid);
    }

    /** The metadata for the game being played. */
    protected GameContent _content;

    // our dependencies
    @Inject protected GameGameRegistry _gameReg;
    @Inject protected ItemManager _itemMan;
}
