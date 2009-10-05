//
// $Id$

package com.threerings.msoy.world.client;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;

import com.threerings.msoy.data.HomePageItem;

/**
 * Provides global services to the world client.
 */
public interface WorldService extends InvocationService
{
    /**
     * Requests the items to populate the home page grid. The expected response is an arry of
     * {@link HomePageItem}. This should eventually take a parameter so that the top 3 "whirled"
     * items are a separate request from the very cachable 6 "what I've done recently" items.
     */
    void getHomePageGridItems (Client client, ResultListener listener);

    /**
     * Request to know the home scene id for the specified owner.
     * @see {@link com.threerings.msoy.room.data.MsoySceneModel}.
     */
    void getHomeId (Client client, byte ownerType, int ownerId, ResultListener listener);

    /**
     * Set the given scene as the owner's home scene
     */
    void setHomeSceneId (Client client, int ownerType, int ownerId, int sceneId,
                         ConfirmListener listener);

    /**
     * Invites the specified member to follow the caller. Passing 0 for the memberId will clear all
     * of the caller's followers.
     */
    void inviteToFollow (Client client, int memberId, InvocationListener listener);

    /**
     * Requests to follow the specified member who must have previously issued an invitation to the
     * caller to follow them. Passing 0 for memberId will clear the caller's following status.
     */
    void followMember (Client client, int memberId, InvocationListener listener);

    /**
     * Removes a player from the caller's list of followers. Passing 0 for memberId will clear all
     * the caller's followers.
     */
    void ditchFollower (Client client, int memberId, InvocationListener listener);

    /**
     * Set the avatar in use by this user.
     */
    void setAvatar (Client client, int avatarId, ConfirmListener listener);
}
