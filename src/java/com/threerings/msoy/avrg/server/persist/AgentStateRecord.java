//
// $Id: GameStateRecord.java 14274 2009-01-08 02:20:15Z mdb $

package com.threerings.msoy.avrg.server.persist;

import com.samskivert.depot.Key;
import com.samskivert.depot.PersistentRecord;
import com.samskivert.depot.annotation.Column;
import com.samskivert.depot.annotation.Entity;
import com.samskivert.depot.annotation.Id;
import com.samskivert.depot.expression.ColumnExp;

/**
 * Maintains persistent server-private state for a given game.
 */
@Entity
public class AgentStateRecord extends PersistentRecord
{
    // AUTO-GENERATED: FIELDS START
    public static final Class<AgentStateRecord> _R = AgentStateRecord.class;
    public static final ColumnExp GAME_ID = colexp(_R, "gameId");
    public static final ColumnExp DATUM_KEY = colexp(_R, "datumKey");
    public static final ColumnExp DATUM_VALUE = colexp(_R, "datumValue");
    // AUTO-GENERATED: FIELDS END

    /** Increment this value if you modify the definition of this persistent
     * object in a way that will result in a change to its SQL counterpart. */
    public static final int SCHEMA_VERSION = 1;

    @Id
    public int gameId;

    @Id
    /** The key that identifies this memory datum. */
    public String datumKey;

    /** A serialized representation of this datum's value. */
    @Column(length=4096)
    public byte[] datumValue;

    /** Used when loading instances from the repository. */
    public AgentStateRecord ()
    {
    }

    /**
     * Creates a memory record from the supplied memory information.
     */
    public AgentStateRecord (int gameId, String key, byte[] data)
    {
        this.gameId = gameId;
        this.datumKey = key;
        this.datumValue = data;
    }

    // AUTO-GENERATED: METHODS START
    /**
     * Create and return a primary {@link Key} to identify a {@link AgentStateRecord}
     * with the supplied key values.
     */
    public static Key<AgentStateRecord> getKey (int gameId, String datumKey)
    {
        return new Key<AgentStateRecord>(
                AgentStateRecord.class,
                new ColumnExp[] { GAME_ID, DATUM_KEY },
                new Comparable[] { gameId, datumKey });
    }
    // AUTO-GENERATED: METHODS END
}
