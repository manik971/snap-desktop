/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esa.snap.gui.nodes;

import org.esa.beam.framework.datamodel.VectorDataNode;

import java.beans.IntrospectionException;

/**
 * @author Norman
 */
public class VDNNode extends PNLeafNode<VectorDataNode> {

    public VDNNode(VectorDataNode vectorDataNode) throws IntrospectionException {
        super(vectorDataNode);
        //setIconBaseWithExtension("org/esa/snap/gui/icons/RsBandAsSwath16.gif");
    }
}
