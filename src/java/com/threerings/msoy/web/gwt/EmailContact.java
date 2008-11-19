//
// $Id$

package com.threerings.msoy.web.gwt;

import com.google.gwt.user.client.rpc.IsSerializable;

import com.threerings.msoy.data.all.MemberName;

public class EmailContact
    implements IsSerializable
{
    /** The contact name. */
    public String name;

    /** The contact email. */
    public String email;

    /** The memberName. */
    public MemberName mname;

    public boolean equals (Object other)
    {
        if (other == null || !(other instanceof EmailContact)) {
            return false;
        }
        return email.equals(((EmailContact)other).email);
    }
}
