////////////////////////////////////////////////////////////////////////////////
//
//  Copyright (C) 2003-2006 Adobe Macromedia Software LLC and its licensors.
//  All Rights Reserved. The following is Source Code and is subject to all
//  restrictions on such code as contained in the End User License Agreement
//  accompanying this product.
//
////////////////////////////////////////////////////////////////////////////////

package mx.controls.dataGridClasses
{

import mx.controls.listClasses.ListBase;
import mx.controls.listClasses.ListBaseContentHolder;

/**
 *  The DataGridLockedRowContentHolder class defines a container in a DataGrid control
 *  of all of the control's item renderers and item editors.
 *  Flex uses it to mask areas of the renderers that extend outside
 *  of the control, and to block certain styles, such as <code>backgroundColor</code>, 
 *  from propagating to the renderers so that highlights and 
 *  alternating row colors can show through the control.
 *
 *  @see mx.controls.DataGrid
 */
public class DataGridLockedRowContentHolder extends ListBaseContentHolder
{

    /**
     *  Constructor.
     *
     *  @param parentList The DataGrid control.
     */
    public function DataGridLockedRowContentHolder(parentList:ListBase)
    {
        super(parentList);
        if (parentList.dataProvider)
            iterator = parentList.dataProvider.createCursor();
    }
    

    /**
     * The measured height of the DataGrid control.
     */
    override public function get measuredHeight():Number
    {
        var rc:Number = rowInfo.length;
        if (rc == 0)
            return 0;

        return rowInfo[rc - 1].y + rowInfo[rc - 1].height;
    }
}

}