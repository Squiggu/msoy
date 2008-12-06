//
// $Id$

package com.threerings.msoy.party.client;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;

public interface PartyBoardService extends InvocationService
{
    /**
     * Retrieve a list of parties.
     * Returns a List<PartyInfo>.
     */
    void getPartyBoard (Client client, String query, ResultListener rl);

    /**
     * Join the specified party.
     * Returns an oid, of the party object on success.
     */
    void joinParty (Client client, int partyId, ResultListener rl);

    /**
     * Create your own party.
     * Returns an oid, of the party object on success.
     */
    void createParty (Client client, String name, int groupId, ResultListener rl);
}
